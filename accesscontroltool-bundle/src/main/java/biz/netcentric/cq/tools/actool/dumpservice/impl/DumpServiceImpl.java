/*
d * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.version.VersionException;
import javax.servlet.ServletOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.QueryBuilder.Direction;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableinstaller.impl.AuthorizableInstallerServiceImpl;
import biz.netcentric.cq.tools.actool.comparators.AcePathComparator;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.comparators.AuthorizableBeanIDComparator;
import biz.netcentric.cq.tools.actool.comparators.JcrCreatedComparator;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementYamlVisitor;
import biz.netcentric.cq.tools.actool.dumpservice.AceDumpData;
import biz.netcentric.cq.tools.actool.dumpservice.CompleteAcDump;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.dumpservice.impl.DumpServiceImpl.Configuration;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.AceWrapper;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;

@Component
@Designate(ocd=Configuration.class)
public class DumpServiceImpl implements ConfigDumpService {

    private static final Logger LOG = LoggerFactory.getLogger(DumpServiceImpl.class);

    private static final String DUMP_FILE_EXTENSION = ".yaml";
    private static final String DUMP_NODE_PREFIX = "dump_";

    public final static int PRINCIPAL_BASED_SORTING = 1;
    public final static int PATH_BASED_SORTING = 2;

    public final static int DENY_ALLOW_ACL_SORTING = 1;
    public final static int NO_ACL_SORTING = 2;

    protected static final int NR_OF_DUMPS_TO_SAVE_DEFAULT = 5;
    static final String DUMP_SERVICE_EXCLUDE_PATHS_PATH = "DumpService.queryExcludePaths";
    static final String DUMP_SERVICE_NR_OF_SAVED_DUMPS = "DumpService.nrOfSavedDumps";
    static final String DUMP_INCLUDE_USERS = "DumpService.includeUsers";

    private String[] queryExcludePaths;
    private int nrOfSavedDumps;
    private boolean includeUsersInDumps = false;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @ObjectClassDefinition(name = "AC Dump Service", 
            description="Service that creates dumps of the current AC configurations (groups&ACEs)",
            id="biz.netcentric.cq.tools.actool.dumpservice.impl.DumpServiceImpl")
    protected static @interface Configuration {
        @AttributeDefinition(name="Number of dumps to save", description="Number of last dumps which get saved in the repository under /var/statistics/achistory")
        int DumpService_nrOfSavedDumps() default 5;
        
        @AttributeDefinition(name="Include users in dumps", description="If selected, also users with their ACEs get added to dumps")
        boolean DumpService_includeUsers() default false;
        
        @AttributeDefinition(name="AC query exclude paths", description="direct children of jcr:root which get excluded from all dumps (also from internal dumps)")
        String[] DumpService_queryExcludePaths() default {"/home", "/jcr:system", "/tmp"};
    }
    
    @Activate
    public void activate(Configuration configuration) throws Exception {
        queryExcludePaths = configuration.DumpService_queryExcludePaths();
        nrOfSavedDumps = configuration.DumpService_nrOfSavedDumps();
        includeUsersInDumps = configuration.DumpService_includeUsers();
    }

    @Override
    public boolean isIncludeUsers() {
        return includeUsersInDumps;
    }

    @Override
    public String getCompletePathBasedDumpsAsString() {
        Session session = null;
        try {

            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            String dump = getCompleteDump(AcHelper.PATH_BASED_ORDER, AcHelper.ACE_ORDER_NONE, session);
            persistDump(dump, session);
            return dump;

        } catch (RepositoryException e) {
            LOG.error("Repository exception in DumpserviceImpl", e);
            return null;
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String getCompletePrincipalBasedDumpsAsString() {
        Session session = null;
        try {

            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            String dump = getCompleteDump(AcHelper.PRINCIPAL_BASED_ORDER, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE, session);
            persistDump(dump, session);
            return dump;
        } catch (RepositoryException e) {
            LOG.error("Repository exception in DumpserviceImpl", e);
            return null;
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    private void persistDump(String dump, Session session) {
        try {
            Node rootNode = HistoryUtils.getAcHistoryRootNode(session);
            createTransientDumpNode(dump, rootNode);
            session.save();
        } catch (RepositoryException e) {
            LOG.error("RepositoryException: {}", e);
        }
    }

    private void createTransientDumpNode(String dump, Node rootNode)
            throws ItemExistsException, PathNotFoundException,
            NoSuchNodeTypeException, LockException, VersionException,
            ConstraintViolationException, RepositoryException,
            ValueFormatException {

        NodeIterator nodeIt = rootNode.getNodes();

        // TreeSet used here since only this type offers the methods first() and
        // last()
        TreeSet<Node> dumpNodes = new TreeSet<Node>(new JcrCreatedComparator());

        Node previousDumpNode = null;

        // get all dump nodes
        while (nodeIt.hasNext()) {
            Node currNode = nodeIt.nextNode();

            if (currNode.getName().startsWith(DUMP_NODE_PREFIX)) {
                dumpNodes.add(currNode);
            }
        }
        // try to get previous dump node
        if (!dumpNodes.isEmpty()) {
            previousDumpNode = dumpNodes.first();
        }
        // is limit of dump nodes to save reached?
        if (dumpNodes.size() > (nrOfSavedDumps - 1)) {
            Node oldestDumpNode = dumpNodes.last();
            oldestDumpNode.remove();
        }
        Node dumpNode = getNewDumpNode(dump, rootNode);

        // order the newest dump node as first child node of ac root node
        if (previousDumpNode != null) {
            rootNode.orderBefore(dumpNode.getName(), previousDumpNode.getName());
        }
    }

    private Node getNewDumpNode(String dump, Node rootNode)
            throws ItemExistsException, PathNotFoundException,
            NoSuchNodeTypeException, LockException, VersionException,
            ConstraintViolationException, RepositoryException,
            ValueFormatException {
        // create necessary child node&properties
        long timestamp = System.currentTimeMillis();
        Node dumpNode = rootNode.addNode(DUMP_NODE_PREFIX + timestamp
                + DUMP_FILE_EXTENSION, "nt:file");

        Node dumpJcrContenNodet = dumpNode
                .addNode("jcr:content", "nt:resource");
        dumpJcrContenNodet.setProperty("jcr:mimeType", "text/plain");
        dumpJcrContenNodet.setProperty("jcr:encoding", "utf-8");
        dumpJcrContenNodet.setProperty("jcr:data", dump);
        return dumpNode;
    }

    /** returns the complete AC dump (groups&ACEs) as String in YAML format
     *
     * @param keyOrder either principals (AcHelper.PRINCIPAL_BASED_ORDER) or node paths (AcHelper.PATH_BASED_ORDER) as keys
     * @param aclOrderInMap specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then
     *            allow)
     * @return String containing complete AC dump */
    private String getCompleteDump(int keyOrder, int aclOrderInMap, Session session) {

        LOG.info("Starting to create dump for "
                + (keyOrder == AcHelper.PRINCIPAL_BASED_ORDER ? "PRINCIPAL_BASED_ORDER" : "PATH_BASED_ORDER"));
        try {

            AceDumpData aceDumpData = createAclDumpMap(
                    keyOrder, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE, // this ORDER is important to keep the ORDER of denies with
                                                                       // "keepOrder"
                    // attribute that is automatically added if needed
                    queryExcludePaths, session);
            Map<String, Set<AceBean>> aclDumpMap = aceDumpData.getAceDump();

            Set<AuthorizableConfigBean> groupBeans = getGroupBeans(session);
            Set<User> usersFromACEs = getUsersFromAces(keyOrder, session, aclDumpMap);
            Set<AuthorizableConfigBean> userBeans = getUserBeans(usersFromACEs);

            String configurationDumpAsString = getConfigurationDumpAsString(aceDumpData, groupBeans,
                    userBeans, aclOrderInMap);
            return configurationDumpAsString;

        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException in DumpServiceImpl: {}", e);
        } catch (IOException e) {
            LOG.error("IOException in AceServiceImpl: {}", e);
        } catch (RepositoryException e) {
            LOG.error("RepositoryException in AceServiceImpl: {}", e);
        }
        return null;
    }

    /** get users from ACEs
     *
     * @param mapOrder
     * @param session
     * @param aclDumpMap
     * @return
     * @throws AccessDeniedException
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException */
    private Set<User> getUsersFromAces(int mapOrder, Session session,
            Map<String, Set<AceBean>> aclDumpMap) throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException {

        Set<User> usersFromACEs = new HashSet<User>();
        UserManager um = ((JackrabbitSession) session).getUserManager();

        // if we have a principal ordered ACE map, all authorizables are
        // contained in the keySet of the map
        if (mapOrder == PRINCIPAL_BASED_SORTING) {
            Set<String> userIds = new HashSet<String>();
            userIds = aclDumpMap.keySet();

            for (String id : userIds) {
                Authorizable authorizable = um.getAuthorizable(new PrincipalImpl(id));
                if (!authorizable.isGroup()) {
                    User user = (User) authorizable;
                    usersFromACEs.add(user);
                }
            }
            // if we have a path ordered ACE map, all authorizables are
            // contained in AceBean properties
        } else if (mapOrder == PATH_BASED_SORTING) {
            for (Map.Entry<String, Set<AceBean>> entry : aclDumpMap.entrySet()) {
                Set<AceBean> aceBeanSet = entry.getValue();

                for (AceBean aceBean : aceBeanSet) {
                    String principalId = aceBean.getPrincipalName();
                    Authorizable authorizable = um.getAuthorizable(new PrincipalImpl(principalId));
                    if (!authorizable.isGroup()) {
                        User user = (User) authorizable;
                        usersFromACEs.add(user);
                    }
                }
            }
        }
        return usersFromACEs;
    }

    @Override
    public String getConfigurationDumpAsString(AceDumpData aceDumpData,
            final Set<AuthorizableConfigBean> groupSet,
            final Set<AuthorizableConfigBean> userSet, final int mapOrder) throws IOException {
        StringBuilder sb = new StringBuilder(20000);

        // add creation date as first line
        String dumpComment = "Dump created: " + new Date();

        new CompleteAcDump(aceDumpData, groupSet, userSet, mapOrder,
                dumpComment, this).accept(new AcDumpElementYamlVisitor(
                        mapOrder, sb));
        return sb.toString();
    }

    @Override
    public Set<AclBean> getACLDumpBeans(final Session session)
            throws RepositoryException {

        List<String> excludeNodesList = Arrays.asList(queryExcludePaths);
        Set<Node> resultNodeSet = QueryHelper.getRepPolicyNodes(session, excludeNodesList);
        Set<AclBean> accessControBeanSet = new LinkedHashSet<AclBean>();

        // assemble big query result set using the query results of the child
        // paths of jcr:root node
        for (Node node : resultNodeSet) {
            try {
                String path = !Constants.REPO_POLICY_NODE.equals(node.getName())
                        ? node.getParent().getPath()
                        : null /*  repo policies are accessed by using a null path */;

                JackrabbitAccessControlList jackrabbitAcl = AccessControlUtils.getAccessControlList(session, path);
                AclBean aclBean = new AclBean(jackrabbitAcl, path);
                accessControBeanSet.add(aclBean);
            } catch (AccessDeniedException e) {
                LOG.error("AccessDeniedException: {}", e);
            } catch (ItemNotFoundException e) {
                LOG.error("ItemNotFoundException: {}", e);
            } catch (RepositoryException e) {
                LOG.error("RepositoryException: {}", e);
            }
        }
        return accessControBeanSet;
    }

    /** Called from JMX console using the OSGi configuration.
     * 
     * @param keyOrder
     * @param aclOrdering
     * @param excludePaths
     * @param session
     * @return */
    public AceDumpData createAclDumpMap(final int keyOrder, final int aclOrdering,
            final String[] excludePaths, Session session) throws ValueFormatException,
            IllegalArgumentException, IllegalStateException,
            RepositoryException {
        return createAclDumpMap(keyOrder, aclOrdering, excludePaths, includeUsersInDumps, session);
    }

    /** returns a Map with holds either principal or path based ACE data
     *
     * @param request
     * @param keyOrder either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
     * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then
     *            allow)
     * @param isFilterACEs
     * @param isIncludeUsers
     * @return
     * @throws RepositoryException */
    @Override
    public AceDumpData createAclDumpMap(final int keyOrder, final int aclOrdering,
            final String[] excludePaths, final boolean isIncludeUsers, Session session) throws RepositoryException {

        AceDumpData aceDumpData = new AceDumpData();
        UserManager um = ((JackrabbitSession) session).getUserManager();
        Map<String, Set<AceBean>> aceMap = new TreeMap<String, Set<AceBean>>();
        Map<String, Set<AceBean>> legacyAceMap = new TreeMap<String, Set<AceBean>>();

        Set<AclBean> aclBeanSet = getACLDumpBeans(session);

        // build a set containing all ACE found in the original order
        for (AclBean aclBean : aclBeanSet) {

            if (aclBean.getAcl() == null) {
                continue;
            }

            boolean allowExistsInListEarlier = false;
            for (AccessControlEntry ace : aclBean.getAcl()
                    .getAccessControlEntries()) {
                if (!(ace instanceof JackrabbitAccessControlEntry)) {
                    throw new IllegalStateException("AC entry is not a JackrabbitAccessControlEntry: " + ace);
                }
                AceWrapper tmpBean = new AceWrapper((JackrabbitAccessControlEntry) ace, aclBean.getJcrPath());
                AceBean tmpAceBean = AcHelper.getAceBean(tmpBean);

                // sets keepOrder true if ACE deny entries are found that are not at top of list
                if (tmpAceBean.isAllow()) {
                    allowExistsInListEarlier = true;
                } else {
                    if (allowExistsInListEarlier && !tmpAceBean.isAllow()) {
                        tmpAceBean.setKeepOrder(true);
                    }
                }

                Authorizable authorizable = um.getAuthorizable(new PrincipalImpl(tmpAceBean.getPrincipalName()));

                // if this group exists under home
                if (authorizable != null) {
                    tmpAceBean.setAuthorizableId(authorizable.getID());
                    if (authorizable.isGroup() || isIncludeUsers) {
                        addBeanToMap(keyOrder, aclOrdering, aceMap, tmpAceBean);
                    }
                }
                // otherwise put in map holding legacy ACEs
                else {
                    addBeanToMap(keyOrder, aclOrdering, legacyAceMap, tmpAceBean);
                }

            }
        }

        aceDumpData.setAceDump(aceMap);

        return aceDumpData;

    }

    private void addBeanToMap(final int keyOrder, final int aclOrdering,
            Map<String, Set<AceBean>> aceMap, AceBean aceBean) {
        if (keyOrder == AcHelper.PRINCIPAL_BASED_ORDER) {
            String principalName = aceBean.getPrincipalName();
            if (!aceMap.containsKey(principalName)) {
                Set<AceBean> aceSet = getNewAceSet(aclOrdering);
                aceSet.add(aceBean);
                aceMap.put(principalName, aceSet);
            } else {
                aceMap.get(principalName).add(aceBean);
            }
        } else if (keyOrder == AcHelper.PATH_BASED_ORDER) {
            String jcrPath = aceBean.getJcrPath();
            if (jcrPath == null) {
                jcrPath = "";
            }
            if (!aceMap.containsKey(jcrPath)) {
                Set<AceBean> aceSet = getNewAceSet(aclOrdering);
                aceSet.add(aceBean);
                aceMap.put(jcrPath, aceSet);
            } else {
                aceMap.get(jcrPath).add(aceBean);
            }
        }
    }

    /** Returns the parent of the group path.
     *
     * @param intermediatePath
     * @return corrected path */
    private String getIntermediatePath(String intermediatePath) {
        String result = StringUtils.substringBeforeLast(intermediatePath, "/");
        return result;
    }

    public void returnAuthorizableDumpAsFile(
            final SlingHttpServletResponse response,
            Set<AuthorizableConfigBean> authorizableSet) throws IOException {
        String mimetype = "application/octet-stream";
        response.setContentType(mimetype);
        ServletOutputStream outStream = null;
        try {
            outStream = response.getOutputStream();
        } catch (IOException e) {
            LOG.error("Exception in AuthorizableDumpUtils: {}", e);
        }

        String fileName = "Authorizable_Dump_"
                + new Date(System.currentTimeMillis());
        response.setHeader("Content-Disposition", "attachment; filename=\""
                + fileName + "\"");

        try {
            writeAuthorizableConfigToStream(authorizableSet, outStream);
        } catch (IOException e) {
            LOG.error("Exception in AuthorizableDumpUtils: {}", e);
        }
        outStream.close();
    }

    public void writeAuthorizableConfigToStream(
            Set<AuthorizableConfigBean> authorizableSet,
            ServletOutputStream outStream) throws IOException {

        outStream.println("- " + Constants.GROUP_CONFIGURATION_KEY + ":");
        outStream.println();

        for (AuthorizableConfigBean bean : authorizableSet) {
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_KEY)
                            + "- " + bean.getAuthorizableId() + ":");
            outStream.println();
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_FIRST_PROPERTY)
                            + "- name: ");
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY)
                            + "isMemberOf: " + bean.getIsMemberOfString());
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY)
                            + "path: " + bean.getPath());
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY)
                            + "isGroup: " + "'" + bean.isGroup() + "'");
            outStream.println();
        }
    }

    /** method that returns the data of users contained in a set as AuthorizableConfigBeans
     *
     * @param usersFromACEs set holding users
     * @return set holding AuthorizableConfigBeans */
    public Set<AuthorizableConfigBean> getUserBeans(Set<User> usersFromACEs)
            throws RepositoryException, UnsupportedRepositoryOperationException {

        Set<AuthorizableConfigBean> userBeans = new TreeSet<AuthorizableConfigBean>(
                new AuthorizableBeanIDComparator());

        // add found users from ACEs to set of authorizables
        if (!usersFromACEs.isEmpty()) {
            for (User user : usersFromACEs) {
                AuthorizableConfigBean newBean = new AuthorizableConfigBean();
                newBean.setAuthorizableId(user.getID());

                String userProfileName = StringUtils
                        .trim(StringUtils.defaultIfEmpty(AcHelper.valuesToString(user.getProperty("profile/givenName")), "")
                                + " " + StringUtils.defaultIfEmpty(AcHelper.valuesToString(user.getProperty("profile/familyName")), ""));
                if (StringUtils.isBlank(userProfileName)) {
                    userProfileName = user.getID();
                }

                newBean.setName(userProfileName);

                String intermediatePath = getIntermediatePath(user.getPath());
                newBean.setPath(intermediatePath);
                newBean.setIsGroup(false);
                newBean.setIsSystemUser(user.isSystemUser());
                // password is not in system and cannot be set

                new HashSet<Authorizable>();
                addDeclaredMembers(user, newBean);
                userBeans.add(newBean);
            }
        }
        return userBeans;
    }

    /** method that fetches all groups from /home and returns their data encapsulated in AuthorizableConfigBeans
     *
     * @param session session with sufficient rights to read group informations
     * @return set holding AuthorizableConfigBeans */
    @Override
    public Set<AuthorizableConfigBean> getGroupBeans(Session session)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException {
        JackrabbitSession js = (JackrabbitSession) session;
        UserManager userManager = js.getUserManager();

        Set<AuthorizableConfigBean> groupBeans = new LinkedHashSet<AuthorizableConfigBean>();

        Iterator<Authorizable> result = userManager
                .findAuthorizables(new Query() {
                    @Override
                    public void build(QueryBuilder builder) {
                        builder.setSortOrder("@rep:principalName",
                                Direction.ASCENDING);
                        builder.setSelector(org.apache.jackrabbit.api.security.user.Group.class);
                    }
                });

        while (result.hasNext()) {
            Group group = (Group) result.next();
            if (group != null) {
                AuthorizableConfigBean bean = new AuthorizableConfigBean();
                bean.setAuthorizableId(group.getID());

                String groupName = StringUtils.defaultIfEmpty(AcHelper.valuesToString(group.getProperty("profile/givenName")),
                        group.getID());
                bean.setName(groupName);

                if (group.hasProperty(AuthorizableInstallerServiceImpl.REP_EXTERNAL_ID)) {
                    bean.setExternalId(AcHelper.valuesToString(group.getProperty(AuthorizableInstallerServiceImpl.REP_EXTERNAL_ID)));
                }

                addDeclaredMembers(group, bean);
                bean.setIsGroup(group.isGroup());
                bean.setPath(getIntermediatePath(group.getPath()));

                groupBeans.add(bean);
            } else {
                LOG.debug("group is null !");
            }
        }
        return groupBeans;

    }

    private void addDeclaredMembers(Authorizable authorizable,
            AuthorizableConfigBean bean) throws RepositoryException {
        Iterator<Group> it = authorizable.declaredMemberOf();
        List<String> memberOfList = new ArrayList<String>();

        while (it.hasNext()) {
            String groupId = it.next().getID();
            if (StringUtils.equals(groupId, EveryonePrincipal.NAME)) {
                continue;
            }
            memberOfList.add(groupId);
        }
        bean.setIsMemberOf(memberOfList.toArray(new String[memberOfList.size()]));
    }

    private Set<AceBean> getNewAceSet(final int aclOrdering) {
        Set<AceBean> aceSet = null;

        if (aclOrdering == AcHelper.ACE_ORDER_NONE) {
            aceSet = new LinkedHashSet<AceBean>();
        } else if (aclOrdering == AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE) {
            aceSet = new TreeSet<AceBean>(new AcePermissionComparator());
        } else if (aclOrdering == AcHelper.ACE_ORDER_ALPHABETICAL) {
            aceSet = new TreeSet<AceBean>(new AcePathComparator());
        }
        return aceSet;
    }

}
