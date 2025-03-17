package biz.netcentric.cq.tools.actool.ims;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.externalusermanagement.ExternalGroupManagement;
import biz.netcentric.cq.tools.actool.ims.IMSUserManagement.Configuration;
import biz.netcentric.cq.tools.actool.ims.request.ActionCommand;
import biz.netcentric.cq.tools.actool.ims.request.AddGroupMembers;
import biz.netcentric.cq.tools.actool.ims.request.AddGroupMembership;
import biz.netcentric.cq.tools.actool.ims.request.CreateGroupStep;
import biz.netcentric.cq.tools.actool.ims.request.RemoveGroupMembership;
import biz.netcentric.cq.tools.actool.ims.request.Step;
import biz.netcentric.cq.tools.actool.ims.request.UserActionCommand;
import biz.netcentric.cq.tools.actool.ims.request.UserGroupActionCommand;
import biz.netcentric.cq.tools.actool.ims.response.AccessToken;
import biz.netcentric.cq.tools.actool.ims.response.ActionCommandResponse;
import biz.netcentric.cq.tools.actool.ims.response.GroupResponse;
import biz.netcentric.cq.tools.actool.ims.response.IMSGroup;
import biz.netcentric.cq.tools.actool.ims.response.IMSUser;
import biz.netcentric.cq.tools.actool.ims.response.UsersInGroupResponse;

/**
 * Managing Adobe IMS groups via the UMAPI.
 * 
 * @see <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/">UMAPI Documentation</a>
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd=Configuration.class)
public class IMSUserManagement implements ExternalGroupManagement {

    @ObjectClassDefinition(name = "AC Tool Adobe IMS User Management", description = "Settings of the API for user management tasks (UMAPI) in the Adobe IMS")
    protected static @interface Configuration {
        @AttributeDefinition(name = "UMAPI Base URL", description = "UMAPI Endpoint Base URL (the common part of all UMAPI endpoints)")
        String umapiBaseUrl() default "https://usermanagement.adobe.io/v2/usermanagement/";
        @AttributeDefinition(name = "Organization ID", description = "The unique identifier for an organization. This is a string of the form A495E53@AdobeOrg where the prefix before the @ is a hexadecimal number. You can find this value as part of the URL path for the organization in the Adobe Admin Console or in the Adobe Developer Console for your User Management integration.")
        String organizationId();
        @AttributeDefinition(name = "Test Only", description = "If true, parameter syntactic and (limited) semantic checking is done, but the specified operations are not performed, so no user/group accounts or group memberships are created, changed, or deleted.")
        boolean isTestOnly();
        @AttributeDefinition(name = "IMS Token Endpoint URL", description = "The URL from which to retrieve the access token.")
        String imsTokenEndpointUrl() default "https://ims-na1.adobelogin.com/ims/token/v3";
        @AttributeDefinition(name = "Client ID", description = "The client ID exposed in the Adobe IO Console for the UMAPI integration used to authorize the session. Also used as \"X-Api-Key\" header value.")
        String clientId();
        @AttributeDefinition(name = "Client Secret", description = "The client secret exposed in the Adobe IO Console for the UMAPI integration used to authorize the session.", type = AttributeType.PASSWORD)
        String clientSecret();
        @AttributeDefinition(name = "OAuth Scopes", description = "Scopes for which the access token is requested.")
        String[] scopes() default { "openid","AdobeID","user_management_sdk" };
        @AttributeDefinition(name = "Connect Timeout", description = "The maximum time to establish the connection with the remote host in milliseconds.")
        int connectTimeout() default 2000;
        @AttributeDefinition(name = "Socket Timeout", description = "The time waiting for data – after establishing the connection; maximum time of inactivity between two data packets. Given in milliseconds.")
        int socketTimeout() default 10000;
        @AttributeDefinition(name = "Overall Timeout", description = "The maximum time updating groups in IMS should take. If expired the operation fails with an exception. Negative values mean no timeout. Given in milliseconds.")
        long overallTimeout() default -1;
        @AttributeDefinition(name = "AEM Product Profiles", description = "The given product profile names are automatically added to each synchronized IMS group. The given product profile names must exist for an AEM product!")
        String[] productProfiles() default {};
        @AttributeDefinition(name = "Group Administrators", description = "The given users are automatically added to each synchronized IMS group as administrator. The given user ids must already exist!")
        String[] groupAdmins() default {};
        @AttributeDefinition(name = "Differential Updates", description = "If true, only groups that have changed are updated. This is only a heuristics and currently leads to only adding new groups but never updating existing group (with same names). Also in some cases the additional request to get all groups may be more expensive than just updating all groups.")
        boolean isDifferentialUpdates() default false;
    }

    public static final Logger LOG = LoggerFactory.getLogger(IMSUserManagement.class);
    private static final int MAX_NUM_COMMANDS_PER_REQUEST = 10;
    private static final int MAX_NUM_GROUPS_PER_ADD_STEP = 10;

    private final Configuration config;
    private final CloseableHttpClient client;

    /**
     * Strategy evaluating the {@code retry-after} response header with status code 429.
     * This class is not thread safe (due to mutable state in {@code retryDelayInSeconds}).
     * Necessary due to <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/api/ActionsRef.html#actionThrottle">throttling of UMAPI requests</a>.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6585#section-4">RFC6585</a>
     * 
     */
    static final class TooManyRequestsRetryStrategy implements ServiceUnavailableRetryStrategy {
        private static final double DEFAULT_MULTIPLIER = 1.5; // increases each time by 50%
        private final int maxRetryCount;
        private final int defaultRetryDelayInSeconds;
        private long retryDelayInMilliseconds;
        private final Random random = new Random();
 
        public TooManyRequestsRetryStrategy(int maxRetryCount, int defaultRetryDelayInSeconds) {
            super();
            this.maxRetryCount = maxRetryCount;
            this.defaultRetryDelayInSeconds = defaultRetryDelayInSeconds;
            this.retryDelayInMilliseconds = defaultRetryDelayInSeconds * 1000l;
        }

        @Override
        public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
            retryDelayInMilliseconds = defaultRetryDelayInSeconds * 1000l;
            boolean shouldRetry = (executionCount <= maxRetryCount && response.getStatusLine().getStatusCode() == 429);
            if (shouldRetry) {
                Header retryAfterHeader = response.getFirstHeader("Retry-After");
                if (retryAfterHeader != null) {
                    // just using the retry-after as is does not work reliably (due to potential concurrent requests)
                    retryDelayInMilliseconds = Integer.parseInt(retryAfterHeader.getValue()) * 1000l;
                    LOG.info("Received 429 status with Retry-After header {}", retryAfterHeader.getValue());
                }
                // make it exponential because the retry-after is unreliable (particularly with multiple requests in parallel)
                retryDelayInMilliseconds *= Math.pow(DEFAULT_MULTIPLIER, executionCount);
                // always add some jitter between 0 and default delay in seconds
                long jitter= random.nextInt(defaultRetryDelayInSeconds) * 1000l;
                retryDelayInMilliseconds += jitter;
                LOG.info("Schedule retry no {} of {} in {} milliseconds (with jitter of {} ms) due to 429 response", executionCount, maxRetryCount, retryDelayInMilliseconds, jitter);
            }
            return shouldRetry;
        }

        @Override
        public long getRetryInterval() {
            return retryDelayInMilliseconds;
        } 
    }

    @Activate
    public IMSUserManagement(Configuration config, @Reference HttpClientBuilderFactory httpClientBuilderFactory) {
        this.config = config;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.connectTimeout())
                .setConnectionRequestTimeout(config.socketTimeout())
                .setSocketTimeout(config.socketTimeout()).build();
        client = httpClientBuilderFactory.newBuilder()
                .setDefaultRequestConfig(requestConfig)
                .setServiceUnavailableRetryStrategy(new TooManyRequestsRetryStrategy(3, 5))
                .build();
    }
    
    @Deactivate
    public void deactivate() throws IOException {
        client.close();
    }

    private URI getUserManagementActionUrl() throws URISyntaxException {
        return new URI(config.umapiBaseUrl()).resolve(new URI(null, null, "action/"+ config.organizationId(), config.isTestOnly() ? "testOnly=true" : null, null));
    }

    private URI getUserManagementGroupsUrl(int page) throws URISyntaxException {
        return new URI(config.umapiBaseUrl()).resolve(new URI(null, null, "groups/"+ config.organizationId() + "/" + page, null));
    }

    private URI getUserManagementUsersInGroupUrl(int page, String groupName) throws URISyntaxException {
        // group names may have spaces and require percent encoding as defined by RFC 3986
        return new URI(config.umapiBaseUrl()).resolve(new URI(null, null, "users/"+ config.organizationId() + "/" + page+ "/" + groupName, null));
    }

    @Override
    public String getLabel() {
        return "Adobe IMS";
    }

    private boolean requireGroupUpdate(Map<String, IMSGroup> existingImsGroups, AuthorizableConfigBean groupConfig) {
        // since group names are case insensitive always compare in lower case
        IMSGroup existingImsGroup = existingImsGroups.get(groupConfig.getAuthorizableId().toLowerCase(Locale.ROOT));
        if (existingImsGroup == null) {
            LOG.debug("Group {} does not exist yet", groupConfig.getAuthorizableId());
            return true;
        } else {
            // this is just a heuristics as the group description is not returned (https://adminconsole.adobe.com/FA907D44536A3C2B0A490D4D@AdobeOrg/support/support-cases/E-001297310)
            return false;
        }
    }

    private static final class UserMembershipChanges {
        private final String userName;
        private final Set<String> groupsToRemove;
        private final Set<String> groupsToAdd;

        public UserMembershipChanges(String userName) {
            this.userName = userName;
            this.groupsToRemove = new LinkedHashSet<>();
            this.groupsToAdd = new LinkedHashSet<>();
        }

        public boolean removeGroup(String group) {
            return groupsToRemove.add(group);
        }

        public boolean addGroup(String group) {
            return groupsToAdd.add(group);
        }

        public boolean addGroups(Collection<String> groups) {
            return groupsToAdd.addAll(groups);
        }
    }

    /**
     * 
     * @param token the api token
     * @param groups the groups whose administrators should be updated
     * @param isAddOnly whether only new group administrators should be added or also existing ones bound to managed groups but no longer configured as admins should be removed
     * @return the list of action commands to be executed to perform the changes
     * @throws IOException
     */
    List<ActionCommand> updateGroupAdminsCommands(String token, Collection<String> groups, boolean isAddOnly) throws IOException {
        List<ActionCommand> actionCommands = new LinkedList<>();
        // optionally make users group administrators
        if (config.groupAdmins() != null && config.groupAdmins().length > 0) {
            Set<String> groupAdmins = new LinkedHashSet<>(Arrays.asList(config.groupAdmins()));
            Map<String, UserMembershipChanges> usersMembershipChanges  = new HashMap<>();
            // for all admin groups collect users to remove and to add
            Set<String> adminGroupNames = groups.stream().map(n -> "_admin_" + n).collect(Collectors.toSet());
            if (!isAddOnly) {
                // check which membership changes are necessary per user
                for (String adminGroupName : adminGroupNames) {
                    Map<String, IMSUser> usersInAdminGroup = getUsersInGroup(token, adminGroupName);
                    // diff against configured admins and remove/add memberhip accordingly
                    Set<String> usersToRemove = new LinkedHashSet<>(usersInAdminGroup.keySet());
                    usersToRemove.removeAll(groupAdmins);
                    for (String userToRemove : usersToRemove) {
                        UserMembershipChanges changes = usersMembershipChanges.computeIfAbsent(userToRemove, UserMembershipChanges::new);
                        changes.removeGroup(adminGroupName);
                    }
                    
                    Set<String> usersToAdd = new LinkedHashSet<>(groupAdmins);
                    usersToAdd.removeAll(usersInAdminGroup.keySet());
                    for (String userToAdd : usersToAdd) {
                        UserMembershipChanges changes = usersMembershipChanges.computeIfAbsent(userToAdd, UserMembershipChanges::new);
                        changes.addGroup(adminGroupName);
                    }
                } 
            } else {
                // just add all adminGroupNames to all admin users
                for (String admin : config.groupAdmins()) {
                    UserMembershipChanges userMembershipChanges = new UserMembershipChanges(admin);
                    userMembershipChanges.addGroups(adminGroupNames);
                    usersMembershipChanges.put(admin, userMembershipChanges);
                } 
            }

            // iterate over all usersMembershipChanges and create user action commands per affected user
            for (UserMembershipChanges changes : usersMembershipChanges.values()) {
                int actionCommandsIndex = actionCommands.size();
                addMembershipSteps(changes.userName, actionCommands.listIterator(actionCommandsIndex), false, changes.groupsToRemove);
                addMembershipSteps(changes.userName, actionCommands.listIterator(actionCommandsIndex), true, changes.groupsToAdd);
            }
            
        }
        return actionCommands;
    }

    /**
     * Adds the necessary user memberhips steps to the next action command from the given iterator. If there is no next action command, a new one is created and added to the list.
     * It makes sure that per action command there are at most {@link #MAX_NUM_GROUPS_PER_ADD_STEP} groups added or removed.
     * @param userName used when new ActionCommand is created
     * @param actionCommandsIterator the ListIterator which is potentially extended with new action commands
     * @param isAdd {@code} true if the group membership should be added, {@code false} if it should be removed
     * @param groupNames the group names to add/remove for the user
     */
    private void addMembershipSteps(String userName, ListIterator<ActionCommand> actionCommandsIterator, boolean isAdd, Collection<String> groupNames) {
        AtomicInteger groupCounter = new AtomicInteger();
        Collection<List<String>> groupNamesBatches = groupNames.stream().collect(Collectors.groupingBy
                    (it->groupCounter.getAndIncrement() / MAX_NUM_GROUPS_PER_ADD_STEP)).values();
        for (List<String> groupNamesBatch : groupNamesBatches) {
            ActionCommand actionCommand;
            if (actionCommandsIterator.hasNext()) {
                actionCommand = actionCommandsIterator.next();
            } else {
                actionCommand = new UserActionCommand(userName);
                actionCommandsIterator.add(actionCommand);
            }
            final Step step;
            if (isAdd) {
                step = new AddGroupMembership(groupNamesBatch);
            } else {
                step = new RemoveGroupMembership(groupNamesBatch);
            }
            actionCommand.addStep(step);
        }
    }

    @Override
    public int updateGroups(Collection<AuthorizableConfigBean> groupConfigs) throws IOException {
        String token = getOAuthServer2ServerToken();
        Map<String, IMSGroup> existingImsGroups = Collections.emptyMap();
        if (config.isDifferentialUpdates()) {
            existingImsGroups = getGroups(token);
        }
        long startEpoch = System.currentTimeMillis();
        List<ActionCommand> actionCommands = new LinkedList<>();
        List<String> updatedGroupNames = new LinkedList<>();
        for (AuthorizableConfigBean groupConfig : groupConfigs) {
            if (config.isDifferentialUpdates() && !requireGroupUpdate(existingImsGroups, groupConfig)) {
                LOG.info("Skip updating IMS group {} as considered up to date", groupConfig.getAuthorizableId());
                continue;
            }
            ActionCommand actionCommand = new UserGroupActionCommand(groupConfig.getAuthorizableId());
            CreateGroupStep createGroupStep = new CreateGroupStep();
            createGroupStep.description = groupConfig.getDescription();
            actionCommand.addStep(createGroupStep);
            // optionally maintain product profile memberships in the group as well
            if (config.productProfiles() != null && config.productProfiles().length > 0) {
                AddGroupMembers addMembers = new AddGroupMembers();
                addMembers.productProfileIds =  new HashSet<>(Arrays.asList(config.productProfiles()));
                actionCommand.addStep(addMembers);
            }
            updatedGroupNames.add(groupConfig.getAuthorizableId());
            actionCommands.add(actionCommand);
        }

        actionCommands.addAll(updateGroupAdminsCommands(token, updatedGroupNames, false));

        // update in batches of 10 commands
        AtomicInteger counter = new AtomicInteger();
        final Collection<List<ActionCommand>> actionCommandsBatches = actionCommands.stream().collect(Collectors.groupingBy
                (it->counter.getAndIncrement() / MAX_NUM_COMMANDS_PER_REQUEST))
                .values();

        for (List<ActionCommand> actionCommandBatch : actionCommandsBatches) {
            ActionCommandResponse response = sendActionCommand(token, actionCommandBatch);
            if (!response.errors.isEmpty()) {
                throw new IOException("Errors updating groups: " + response.errors + " for request " + getRequestInfo(response.associatedRequest));
            }
            if (!response.warnings.isEmpty()) {
                LOG.warn("Some warnings during updating groups with request {}", getRequestInfo(response.associatedRequest));
                response.warnings.stream().forEach(w -> LOG.warn("Warning updating a group: {}", w));
            }
            if (config.overallTimeout() > 0 && System.currentTimeMillis() - startEpoch > config.overallTimeout()) {
                throw new IOException("Timeout updating IMS groups, exceeded overall timeout of " + config.overallTimeout() + " ms");
            }
        }
        return updatedGroupNames.size();
    }

    static String getRequestInfo(HttpRequest request) throws IOException {
        if (request == null) {
            return "Unknown";
        }
        StringBuilder requestInfo = new StringBuilder();
        requestInfo.append(request);
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            entity.writeTo(bs);
            requestInfo.append("\nwith payload:\n").append(new String(bs.toByteArray()));
        }
        return requestInfo.toString();
    }

    private void setHttpAuthenticationHeaders(HttpMessage httpMessage, String token) {
        httpMessage.setHeader("Authorization", "Bearer " + token);
        httpMessage.setHeader("X-Api-Key", config.clientId());
        
    }

    /** 
     * Retrieves all groups and product profiles with the API endpoint described at <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/api/group.html">Get User Groups and Product Profiles</a>.
     * <p>
     * Maximum 5 requests per minute per a client.
     * @param token the access token
     * @throws IOException 
     * @return a map with group names (lower-case) as keys and {@link IMSGroup}s as values
     */
    Map<String, IMSGroup> getGroups(String token) throws IOException {
        int page = 0;
        boolean isLastPage = false;
        Map<String, IMSGroup> groups = new HashMap<>();
        while (!isLastPage) {
            GroupResponse response = getGroups(token, page++);
            isLastPage = response.isLastPage;
            groups.putAll(response.groups.stream()
                    .filter(g -> "USER_GROUP".equals(g.type))
                    .collect(Collectors.toMap(
                            g -> g.getGroupName().toLowerCase(Locale.ROOT), 
                            Function.identity(),
                            (a,b) -> {
                                LOG.warn("Duplicate group name {} found, keeping first occurrence", a.getGroupName());
                                return a;
                            })));
        }
        return groups;
    }

    GroupResponse getGroups(String token, int page) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpGet httpGet;
        try {
            httpGet = new HttpGet(getUserManagementGroupsUrl(page));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not create valid URI from configuration", e);
        }
        setHttpAuthenticationHeaders(httpGet, token);
        ResponseHandler<GroupResponse> rh = new ResponseHandler<GroupResponse>() {
            @Override
            public GroupResponse handleResponse(
                    final HttpResponse response) throws IOException {
                StatusLine statusLine = response.getStatusLine();
                HttpEntity entity = response.getEntity();
                if (statusLine.getStatusCode() >= 300) {
                    throw new HttpResponseException(
                            statusLine.getStatusCode(),
                            statusLine.getReasonPhrase() + ", body:" + EntityUtils.toString(entity) + ", for request " + getRequestInfo(httpGet));
                }
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content for request " + getRequestInfo(httpGet));
                }
                System.out.println(EntityUtils.toString(entity));
                GroupResponse groupResponse = objectMapper.readValue(entity.getContent(), GroupResponse.class);
                groupResponse.associatedRequest = httpGet;
                return groupResponse;
            }
        };
        LOG.debug("Calling UMAPI via {}", httpGet);
        return client.execute(httpGet, rh);
    }

    /** 
     * Retrieves all members of a group with the API endpoint described at <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/api/getUsersByGroup.html">Get Users in a User Group or Product Profile</a>.
     * <p>
     * Maximum 25 requests per minute per a client.
     * @param token the access token
     * @param name the group name
     * @throws IOException 
     * @return a map with group names as keys and {@link IMSGroup}s as values
     */
    Map<String, IMSUser> getUsersInGroup(String token, String name) throws IOException {
        int page = 0;
        boolean isLastPage = false;
        Map<String, IMSUser> users = new HashMap<>();
        while (!isLastPage) {
            UsersInGroupResponse response = getUsersInGroup(token, name, page++);
            isLastPage = response.isLastPage;
            users.putAll(response.users.stream().collect(Collectors.toMap(IMSUser::getUsername, Function.identity())));
        }
        return users;
    }

    UsersInGroupResponse getUsersInGroup(String token, String name, int page) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpGet httpGet;
        try {
            httpGet = new HttpGet(getUserManagementUsersInGroupUrl(page, name));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not create valid URI from configuration", e);
        }
        setHttpAuthenticationHeaders(httpGet, token);
        ResponseHandler<UsersInGroupResponse> rh = new ResponseHandler<UsersInGroupResponse>() {
            @Override
            public UsersInGroupResponse handleResponse(
                    final HttpResponse response) throws IOException {
                StatusLine statusLine = response.getStatusLine();
                HttpEntity entity = response.getEntity();
                if (statusLine.getStatusCode() >= 300) {
                    throw new HttpResponseException(
                            statusLine.getStatusCode(),
                            statusLine.getReasonPhrase() + ", body:" + EntityUtils.toString(entity) + ", for request " + getRequestInfo(httpGet));
                }
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content for request " + getRequestInfo(httpGet));
                }
                UsersInGroupResponse groupResponse = objectMapper.readValue(entity.getContent(), UsersInGroupResponse.class);
                groupResponse.associatedRequest = httpGet;
                return groupResponse;
            }
        };
        LOG.debug("Calling UMAPI via {}", httpGet);
        return client.execute(httpGet, rh);
    }

    private ActionCommandResponse sendActionCommand(String token, Collection<ActionCommand> actions) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpPost httpPost;
        try {
            httpPost = new HttpPost(getUserManagementActionUrl());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not create valid URI from configuration", e);
        }
        String jsonPayload = objectMapper.writeValueAsString(actions);
        httpPost.setEntity(new StringEntity(jsonPayload, ContentType.create("application/json"))); // must be without charset
        setHttpAuthenticationHeaders(httpPost, token);
        ResponseHandler<ActionCommandResponse> rh = new ResponseHandler<ActionCommandResponse>() {
            @Override
            public ActionCommandResponse handleResponse(
                    final HttpResponse response) throws IOException {
                StatusLine statusLine = response.getStatusLine();
                HttpEntity entity = response.getEntity();
                if (statusLine.getStatusCode() >= 300) {
                    throw new HttpResponseException(
                            statusLine.getStatusCode(),
                            statusLine.getReasonPhrase() + ", body:" + EntityUtils.toString(entity) + ", for request " + getRequestInfo(httpPost));
                }
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content for request " + getRequestInfo(httpPost));
                }
                ActionCommandResponse actionCommandResponse = objectMapper.readValue(entity.getContent(), ActionCommandResponse.class);
                actionCommandResponse.associatedRequest = httpPost;
                return actionCommandResponse;
            }
        };
        LOG.debug("Calling UMAPI via {}", httpPost);
        return client.execute(httpPost, rh);
    }

    /**
     * Requests an access token using the OAuth Server to Server authentication flow (OAuth 2.0 client credential grant).
     * It is valid for 24 hours.
     * @return the access token
     * @throws IOException 
     * @see <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/UM_Authentication.html">OAuth Server to Server Authentication</a>
     */
    String getOAuthServer2ServerToken() throws IOException {
        HttpPost httpPost = new HttpPost(config.imsTokenEndpointUrl());
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", config.clientId()));
        params.add(new BasicNameValuePair("client_secret", config.clientSecret()));
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        params.add(new BasicNameValuePair("scope", String.join(",", config.scopes())));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        httpPost.setEntity(entity);

        ResponseHandler<AccessToken> rh = new ResponseHandler<AccessToken>() {
            @Override
            public AccessToken handleResponse(
                    final HttpResponse response) throws IOException {
                StatusLine statusLine = response.getStatusLine();
                HttpEntity entity = response.getEntity();
                if (statusLine.getStatusCode() >= 300) {
                    throw new HttpResponseException(
                            statusLine.getStatusCode(),
                            statusLine.getReasonPhrase() + ", body:" + EntityUtils.toString(entity));
                }
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content");
                }
                ObjectMapper objectMapper = new ObjectMapper();
                ContentType contentType = ContentType.getOrDefault(entity);
                Charset charset = contentType.getCharset();
                try (Reader reader = new InputStreamReader(entity.getContent(), charset)) {
                    return objectMapper.readValue(reader, AccessToken.class);
                }
            }
        };
        AccessToken token = client.execute(httpPost, rh);
        return token.token;
    }
}
