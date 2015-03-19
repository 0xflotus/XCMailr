/**  
 *  Copyright 2013 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import models.Domain;
import models.MailTransaction;
import models.PageList;
import models.User;
import models.UserFormData;
import ninja.Context;
import ninja.FilterWith;
import ninja.Result;
import ninja.Results;
import ninja.i18n.Messages;
import ninja.params.Param;
import ninja.params.PathParam;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import conf.XCMailrConf;
import etc.HelperUtils;
import filters.AdminFilter;
import filters.SecureFilter;
import filters.WhitelistFilter;

/**
 * Handles all Actions for the Administration-Section
 * 
 * @author Patrick Thum, Xceptance Software Technologies GmbH, Germany.
 */

@FilterWith(
    {
        SecureFilter.class, AdminFilter.class
    })
@Singleton
public class AdminHandler
{
    @Inject
    XCMailrConf xcmConfiguration;

    @Inject
    Messages messages;

    @Inject
    MailrMessageSenderFactory mailSender;

    @Inject
    CachingSessionHandler cachingSessionHandler;

    private static final Pattern PATTERN_DOMAINS = Pattern.compile("^[a-z0-9]+([\\-\\.]{1}[a-z0-9]+)*\\.[a-z]{2,6}");

    /**
     * Shows the Administration-Index-Page<br/>
     * GET site/admin
     * 
     * @param context
     *            the Context of this Request
     * @return the Admin-Index-Page
     */
    public Result showAdmin(Context context)
    {
        return Results.html();
    }

    /**
     * Shows a List of all {@link models.User Users} in the DB <br/>
     * GET site/admin/users
     * 
     * @param context
     *            the Context of this Request
     * @return a List of all Users
     */
    public Result showUsers(Context context)
    {
        Result result = Results.html();
        User user = context.getAttribute("user", User.class);
        // render the userID in the result to identify the row where no buttons will be shown
        result.render("uid", user.getId());
        // set a default number or the number which the user had chosen
        HelperUtils.parseEntryValue(context, xcmConfiguration.APP_DEFAULT_ENTRYNO);

        // get the default number of entries per page
        int entries = Integer.parseInt(context.getSession().get("no"));

        String searchString = context.getParameter("s", "");
        // generate the paged-list to get pagination in the template
        PageList<User> pagedUserList = new PageList<User>(User.findUserLike(searchString), entries);
        // add the user-list
        result.render("users", pagedUserList);

        if (!searchString.equals(""))
        { // there is a searchString
            result.render("searchValue", searchString);
        }
        return result;
    }

    /**
     * Shows a List of all {@link models.Status Status} in the DB <br/>
     * GET site/admin/summedtx
     * 
     * @param context
     *            the Context of this Request
     * @return a List of all Status
     */
    public Result showSummedTransactions(Context context)
    {
        return Results.html().render("stats", MailTransaction.getStatusList());
    }

    /**
     * Shows a paginated List of all {@link models.MailTransaction Mailtransactions} in the DB <br/>
     * GET site/admin/mtxs
     * 
     * @param context
     *            the Context of this Request
     * @return the Page to show paginated MailTransactions
     */
    public Result pagedMTX(Context context, @Param("p") int page)
    {
        // set a default number or the number which the user had chosen
        HelperUtils.parseEntryValue(context, xcmConfiguration.APP_DEFAULT_ENTRYNO);
        // get the default number of entries per page
        int entries = Integer.parseInt(context.getSession().get("no"));

        // set a default value if there's no one given
        page = (page == 0) ? 1 : page;

        // generate the paged-list to get pagination on the page
        PageList<MailTransaction> pagedMailTransactionList = new PageList<MailTransaction>(
                                                                                           MailTransaction.getSortedAndLimitedList(xcmConfiguration.MTX_LIMIT),
                                                                                           entries);
        return Results.html().render("plist", pagedMailTransactionList).render("curPage", page);
    }

    /**
     * Delete a time-specified number of MailTransactions <br/>
     * GET /admin/mtxs/delete/{time}
     * 
     * @param time
     *            the time in days (all before will be deleted)
     * @return to the MailTransaction-Page
     */

    public Result deleteMTXProcess(@PathParam("time") Integer time, Context context)
    {
        if (time == null)
            return Results.redirect(context.getContextPath() + "/admin/mtxs");

        if (time == -1)
        { // all entries will be deleted
            MailTransaction.deleteTxInPeriod(null);
        }
        else
        {
            // calculate the time and delete all entries before
            DateTime dt = DateTime.now().minusDays(time);
            MailTransaction.deleteTxInPeriod(dt.getMillis());
        }
        return Results.redirect(context.getContextPath() + "/admin/mtxs");
    }

    /**
     * Activates or Deactivates the User with the given ID <br/>
     * POST /admin/activate/{id}
     * 
     * @param userId
     *            ID of a User
     * @param context
     *            the Context of this Request
     * @return the User-Overview-Page (/admin/users)
     */
    public Result activateUserProcess(@PathParam("id") Long userId, Context context)
    {
        // get the user who executes this action
        User executingUser = context.getAttribute("user", User.class);
        if (executingUser.getId() == userId)
            // the admin wants to disable his own account, this is not allowed
            return Results.redirect(context.getContextPath() + "/admin/users");

        // activate or deactivate the user
        boolean active = User.activate(userId);

        // generate the (de-)activation-information mail and send it to the user
        User user = User.getById(userId);
        String from = xcmConfiguration.ADMIN_ADDRESS;
        String host = xcmConfiguration.MB_HOST;

        Optional<String> optLanguage = Optional.of(user.getLanguage());

        // generate the message title
        String subject = messages.get(active ? "user_Activate_Title" : "user_Deactivate_Title", optLanguage, host)
                                 .get();
        // generate the message body
        String content = messages.get(active ? "user_Activate_Message" : "user_Deactivate_Message", optLanguage,
                                      user.getForename()).get();
        // send the mail
        mailSender.sendMail(from, user.getMail(), content, subject);
        if (!active)
        { // delete the sessions of this user
            cachingSessionHandler.deleteUsersSessions(User.getById(userId));
        }
        return Results.redirect(context.getContextPath() + "/admin/users");
    }

    /**
     * Pro- or Demotes the {@link models.User User} with the given ID <br/>
     * POST /admin/promote/{id}
     * 
     * @param userId
     *            ID of a {@link models.User User}
     * @param context
     *            the Context of this Request
     * @return the User-Overview-Page (/admin/users)
     */
    public Result promoteUserProcess(@PathParam("id") Long userId, Context context)
    {
        User user = context.getAttribute("user", User.class);

        if (user.getId() != userId)
        { // the user to pro-/demote is not the user who performs this action
            User.promote(userId);
            // update all of the sessions
            cachingSessionHandler.updateUsersSessions(User.getById(userId));
        }
        return Results.redirect(context.getContextPath() + "/admin/users");
    }

    /**
     * Handles the {@link models.User User}-Delete-Function <br/>
     * POST /admin/delete/{id}
     * 
     * @param deleteUserId
     *            the ID of a {@link models.User User}
     * @param context
     *            the Context of this Request
     * @return the User-Overview-Page (/admin/users)
     */
    public Result deleteUserProcess(@PathParam("id") Long deleteUserId, Context context)
    {
        User user = context.getAttribute("user", User.class);

        if (user.getId() != deleteUserId)
        { // the user to delete is not the user who performs this action
            cachingSessionHandler.deleteUsersSessions(User.getById(deleteUserId));
            User.delete(deleteUserId);
        }

        return Results.redirect(context.getContextPath() + "/admin/users");
    }

    /**
     * Handles JSON-Requests from the search <br/>
     * GET /admin/usersearch
     * 
     * @param context
     *            the Context of this Request
     * @return a JSON-Array with the userdatalist
     */
    public Result jsonUserSearch(Context context)
    {
        List<User> userList;
        String searchString = context.getParameter("s", "");

        userList = (searchString.equals("")) ? new ArrayList<User>() : User.findUserLike(searchString);

        UserFormData userData;
        List<UserFormData> userDatalist = new ArrayList<UserFormData>();

        // GSON can't handle with cyclic references (the 1:m relation between user and MBox will end up in a cycle)
        // so we need to transform the data which does not contain the reference
        for (User currentUser : userList)
        {
            userData = UserFormData.prepopulate(currentUser);
            userDatalist.add(userData);
        }
        return Results.json().render(userDatalist);
    }

    /**
     * Shows a page that contains a list of all allowed domains of emails for registration <br/>
     * GET /admin/whitelist
     * 
     * @param context
     *            the Context of this Request
     * @return the domain-whitelist page
     */
    @FilterWith(WhitelistFilter.class)
    public Result showDomainWhitelist(Context context)
    {
        List<Domain> domainList = Domain.getAll();

        return Results.html().render("domains", domainList);
    }

    /**
     * Displays the Remove-Domain Page to decide whether the admin wants to delete all users to the requested domain or
     * just the domain itself <br/>
     * POST /admin/whitelist/remove
     * 
     * @param context
     *            the Context of this Request
     * @param remDomainId
     *            the Id of the Domain-Object
     * @return the removeDomainConfirmation-Page
     */
    @FilterWith(WhitelistFilter.class)
    public Result callRemoveDomain(Context context, @Param("removeDomainsSelection") Long remDomainId)
    {
        Domain domain = Domain.getById(remDomainId);
        Result result = Results.html().template("/views/AdminHandler/removeDomainConfirmation.ftl.html");
        return result.render("domain", domain);
    }

    /**
     * handles the action requested in the removeDomainConfirmation
     * 
     * @param context
     *            the Context of this Request
     * @param action
     *            the action to do (abort, deleteUsersAndDomain or deleteDomain)
     * @param domainId
     *            the Id of the Domain-Object
     * @return to the whitelist overview page
     */
    @FilterWith(WhitelistFilter.class)
    public Result handleRemoveDomain(Context context, @Param("action") String action, @Param("domainId") long domainId)
    {
        Result result = Results.redirect(context.getContextPath() + "/admin/whitelist");
        if (StringUtils.isBlank(action))
            return result;

        if (action.equals("deleteUsersAndDomain"))
        {
            Domain domain = Domain.getById(domainId);
            List<User> usersToDelete = User.getUsersOfDomain(domain.getDomainname());

            for (User userToDelete : usersToDelete)
            { // delete the sessions of the users and the account
                cachingSessionHandler.deleteUsersSessions(userToDelete);
                User.delete(userToDelete.getId());
            }
            domain.delete();
        }
        else if (action.equals("deleteDomain"))
        {// just delete the domain
            Domain.delete(domainId);
        }

        // if no action matches or the actions had been executed, redirect
        return result;
    }

    /**
     * Adds a Domain to the whitelist
     * 
     * @param context
     *            the Context of this Request
     * @param domainName
     *            the domain-name to add
     * @return to the whitelist-page
     */
    @FilterWith(WhitelistFilter.class)
    public Result addDomain(Context context, @Param("domainName") String domainName)
    {
        Result result = Results.redirect(context.getContextPath() + "/admin/whitelist");
        if (StringUtils.isBlank(domainName))
        {
            // the input-string was empty
            context.getFlashScope().error("adminAddDomain_Flash_EmptyField");
            return result;
        }

        if (!PATTERN_DOMAINS.matcher(domainName).matches())
        { // the validation of the domain-name failed
            context.getFlashScope().error("adminAddDomain_Flash_InvalidDomain");
            return result;
        }

        if (Domain.exists(domainName))
        { // the domain-name is already part of the domain-list
            context.getFlashScope().error("adminAddDomain_Flash_DomainExists");
            return result;
        }

        Domain domain = new Domain(domainName);
        domain.save();
        context.getFlashScope().success("adminAddDomain_Flash_Success");

        return result;
    }
}
