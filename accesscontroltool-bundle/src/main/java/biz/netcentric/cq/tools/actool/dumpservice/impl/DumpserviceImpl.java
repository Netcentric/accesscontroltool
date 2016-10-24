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
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.QueryBuilder.Direction;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.AcePathComparator;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.comparators.AuthorizableBeanIDComparator;
import biz.netcentric.cq.tools.actool.comparators.JcrCreatedComparator;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementYamlVisitor;
import biz.netcentric.cq.tools.actool.dumpservice.AceDumpData;
import biz.netcentric.cq.tools.actool.dumpservice.CompleteAcDump;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.AceWrapper;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.installationhistory.impl.HistoryUtils;

@Service
@Component(metatype = true, label = "AC Dump Service", description = "Service that creates dumps of the current AC configurations (groups&ACEs)")
@Properties({

        @Property(label = "Number of dumps to save", name = DumpserviceImpl.DUMP_SERVICE_NR_OF_SAVED_DUMPS, value = "5", description = "number of last dumps which get saved in CRX under /var/statistics/achistory"),
        @Property(label = "Include user ACEs in dumps", name = DumpserviceImpl.DUMP_INCLUDE_USERS, boolValue = false, description = "if selected, also user based ACEs (and their respective users) get added to dumps"),
        @Property(label = "AC query exclude paths", name = DumpserviceImpl.DUMP_SERVICE_EXCLUDE_PATHS_PATH, value = {
                "/home", "/jcr:system",
                "/tmp" }, description = "direct children of jcr:root which get excluded from all dumps (also from internal dumps)") })
public class DumpserviceImpl implements Dumpservice {

    private static final Logger LOG = LoggerFactory
            .getLogger(DumpserviceImpl.class);

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


    @Reference
    private SlingRepository repository;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties,
            final ComponentContext context) throws Exception {
        updateProperties(properties, context);
    }

    @Modified
    public void modified(@SuppressWarnings("rawtypes") final Map properties,
            final ComponentContext context) throws Exception {
        updateProperties(properties, context);
    }

    private void updateProperties(final Map properties,
            final ComponentContext context) {
        queryExcludePaths = PropertiesUtil.toStringArray(
                properties.get(DUMP_SERVICE_EXCLUDE_PATHS_PATH), null);
        nrOfSavedDumps = PropertiesUtil.toInteger(
                properties.get(DUMP_SERVICE_NR_OF_SAVED_DUMPS),
                NR_OF_DUMPS_TO_SAVE_DEFAULT);
        includeUsersInDumps = PropertiesUtil.toBoolean(
                properties.get(DUMP_INCLUDE_USERS), false);

    }


    @Override
    public boolean isIncludeUsers() {
        return includeUsersInDumps;
    }

    @Override
    public String[] getQueryExcludePaths() {
        return queryExcludePaths;
    }

    @Override
    public String getCompletePathBasedDumpsAsString() {
        String dump = getCompleteDump(AcHelper.PATH_BASED_ORDER, AcHelper.ACE_ORDER_NONE);
        persistDump(dump);
        return dump;
    }

    @Override
    public String getCompletePrincipalBasedDumpsAsString() {
        String dump = getCompleteDump(AcHelper.PRINCIPAL_BASED_ORDER, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);
        persistDump(dump);
        return dump;
    }

    private void persistDump(String dump) {
        Session session = null;
        try {
            session = repository.loginAdministrative(null);
            Node rootNode = HistoryUtils.getAcHistoryRootNode(session);
            createTransientDumpNode(dump, rootNode);
            session.save();
        } catch (RepositoryException e) {
            LOG.error("RepositoryException: {}", e);
        } finally {
            if (session != null) {
                session.logout();
            }
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
     * @param aclOrderInMap specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then allow)
     * @return String containing complete AC dump */
    private String getCompleteDump(int keyOrder, int aclOrderInMap) {
        Session session = null;

        LOG.info("Starting to create dump for "
                + (keyOrder == AcHelper.PRINCIPAL_BASED_ORDER ? "PRINCIPAL_BASED_ORDER" : "PATH_BASED_ORDER"));
        try {
            session = repository.loginAdministrative(null);
            AceDumpData aceDumpData = createAclDumpMap(session,
                    keyOrder, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE, // this ORDER is important to keep the ORDER of denies with "keepOrder"
                                                             // attribute that is automatically added if needed
                    queryExcludePaths);
            Map<String, Set<AceBean>> aclDumpMap = aceDumpData.getAceDump();

            // Map<String, Set<AceBean>> legacyAclDumpMap =
            // aceDumpData.getLegacyAceDump();
            Set<AuthorizableConfigBean> groupBeans = getGroupBeans(session);
            Set<User> usersFromACEs = getUsersFromAces(keyOrder, session, aclDumpMap);
            Set<AuthorizableConfigBean> userBeans = getUserBeans(usersFromACEs);

            String configurationDumpAsString = getConfigurationDumpAsString(aceDumpData, groupBeans,
                    userBeans, aclOrderInMap);
            return configurationDumpAsString;
        } catch (ValueFormatException e) {
            LOG.error("ValueFormatException in AceServiceImpl: {}", e);
        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException in AceServiceImpl: {}", e);
        } catch (IOException e) {
            LOG.error("IOException in AceServiceImpl: {}", e);
        } catch (RepositoryException e) {
            LOG.error("RepositoryException in AceServiceImpl: {}", e);
        } finally {
            if (session != null) {
                session.logout();
            }
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
                Authorizable authorizable = um.getAuthorizable(id);
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
                    Authorizable authorizable = um.getAuthorizable(principalId);
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

        // check excludePaths for existence
        for (String path : excludeNodesList) {
            try {
                if (!session.itemExists(path)) {
                    LOG.error(
                            "Query exclude path: {} doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!",
                            path);
                    throw new IllegalArgumentException(
                            "Query exclude path: "
                                    + path
                                    + " doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!");
                }
            } catch (RepositoryException e) {
                LOG.error("RepositoryException: {}", e);
                throw e;
            }
        }

        Set<Node> resultNodeSet = QueryHelper.getRepPolicyNodes(session,
                excludeNodesList);
        Set<AclBean> accessControBeanSet = new LinkedHashSet<AclBean>();

        // assemble big query result set using the query results of the child
        // paths of jcr:root node
        for (Node node : resultNodeSet) {
            try {
                accessControBeanSet.add(new AclBean(AccessControlUtils
                        .getAccessControlList(session, node.getParent()
                                .getPath()),
                        node.getParent().getPath()));
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

    @Override
    public AceDumpData createAclDumpMap(final Session session,
            final int keyOrder, final int aclOrdering,
            final String[] excludePaths) throws ValueFormatException,
                    IllegalArgumentException, IllegalStateException,
                    RepositoryException {
        return createAclDumpMap(session, keyOrder, aclOrdering, excludePaths, includeUsersInDumps);
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
     * @throws ValueFormatException
     * @throws IllegalStateException
     * @throws RepositoryException */
    private AceDumpData createAclDumpMap(final Session session,
            final int keyOrder, final int aclOrdering,
            final String[] excludePaths,
            final boolean isIncludeUsers) throws ValueFormatException,
                    IllegalArgumentException, IllegalStateException,
                    RepositoryException {
        AceDumpData aceDumpData = new AceDumpData();
        UserManager um = ((JackrabbitSession) session).getUserManager();
        Map<String, Set<AceBean>> aceMap = null;
        Map<String, Set<AceBean>> legacyAceMap = new TreeMap<String, Set<AceBean>>();

        if (keyOrder == AcHelper.PRINCIPAL_BASED_ORDER) { // principal based
            aceMap = new TreeMap<String, Set<AceBean>>();
        } else if (keyOrder == AcHelper.PATH_BASED_ORDER) { // path based
            aceMap = new TreeMap<String, Set<AceBean>>();
        }

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

                Authorizable authorizable = um.getAuthorizable(tmpAceBean
                        .getPrincipalName());

                // if this group exists under home
                if (authorizable != null) {
                    if (authorizable.isGroup() || isIncludeUsers) {
                        addBeanToMap(keyOrder, aclOrdering, aceMap, tmpAceBean);
                    }
                }
                // otherwise put in map holding legacy ACEs
                else {
                    addBeanToMap(keyOrder, aclOrdering, legacyAceMap,
                            tmpAceBean);
                }

            }
        }

        aceDumpData.setAceDump(aceMap);
        aceDumpData.setLegacyAceDump(legacyAceMap);

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
            if (!aceMap.containsKey(jcrPath)) {
                Set<AceBean> aceSet = getNewAceSet(aclOrdering);
                aceSet.add(aceBean);
                aceMap.put(jcrPath, aceSet);
            } else {
                aceMap.get(jcrPath).add(aceBean);
            }
        }
    }

    /** removes the name of the group node itself (groupID) from the intermediate path
     *
     * @param intermediatePath
     * @param groupID
     * @return corrected path if groupID was found at the end of the intermediatePath, otherwise original path */
    private String getIntermediatePath(String intermediatePath,
            final String groupID) {
        int index = StringUtils.lastIndexOf(intermediatePath, "/" + groupID);
        if (index != -1) {
            intermediatePath = intermediatePath.replace(
                    intermediatePath.substring(index), "");
        }
        return intermediatePath;
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
                            + "- " + bean.getPrincipalID() + ":");
            outStream.println();
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_FIRST_PROPERTY)
                            + "- name: ");
            outStream
                    .println(AcHelper
                            .getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY)
                            + "memberOf: " + bean.getMemberOfString());
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
                newBean.setPrincipalID(user.getID());
                String intermediatePath = getIntermediatePath(user.getPath(),
                        user.getID());
                newBean.setPath(intermediatePath);
                newBean.setIsGroup(false);
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
                bean.setPrincipalID(group.getID());
                addDeclaredMembers(group, bean);
                bean.setIsGroup(group.isGroup());
                bean.setPath(getIntermediatePath(group.getPath(), group.getID()));

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
            memberOfList.add(it.next().getID());
        }
        bean.setMemberOf(memberOfList.toArray(new String[memberOfList.size()]));
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
