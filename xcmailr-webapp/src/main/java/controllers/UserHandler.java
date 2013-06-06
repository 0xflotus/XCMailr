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

import java.util.Arrays;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import conf.XCMailrConf;
import models.EditUsr;
import models.User;
import ninja.Context;
import ninja.FilterWith;
import ninja.Result;
import ninja.Results;
import ninja.i18n.Lang;
import ninja.i18n.Messages;
import ninja.validation.JSR303Validation;
import ninja.validation.Validation;
import filters.SecureFilter;

/**
 * Handles the actions of the User-Object
 * 
 * @see User
 * @author Patrick Thum 2012 released under Apache 2.0 License
 */
@FilterWith(SecureFilter.class)
@Singleton
public class UserHandler
{
    @Inject
    MemCachedSessionHandler mcsh;

    @Inject
    XCMailrConf xcmConf;

    @Inject
    Messages msg;
    
    @Inject
    Lang lang;

    /**
     * Edits the {@link User}-Data <br/>
     * POST /user/edit
     * 
     * @param context
     *            the Context of this Request
     * @param edt
     *            the Data of the User-Edit-Form
     * @param validation
     *            Form validation
     * @return the Edit-Page again
     */
    public Result editUser(Context context, @JSR303Validation EditUsr edt, Validation validation)
    {

        User usr = (User) mcsh.get(context.getSessionCookie().getId());

        if (validation.hasViolations())
        { // the filled form has errors
            edt.setPw("");
            edt.setPwn1("");
            edt.setPwn2("");
            context.getFlashCookie().error("i18nMsg_FormErr", (Object) null);
            return Results.redirect("/user/edit");
        }
        else
        { // the form is filled correctly

            // don't let the user register with one of our domains
            // (prevent mail-loops)
            String mail = edt.getMail();
            String domPart = mail.split("@")[1];
            if (Arrays.asList(xcmConf.DM_LIST).contains(domPart))
            {
                context.getFlashCookie().error("i18nMsg_NoLoop", (Object) null);
                edt.setMail(usr.getMail());
                edt.setPw("");
                edt.setPwn1("");
                edt.setPwn2("");
                return Results.html().template("/views/UserHandler/editUserForm.ftl.html").render(edt);
            }

            String pw1 = edt.getPwn1();
            String pw2 = edt.getPwn2();

            if (usr.checkPasswd(edt.getPw()))
            { // the user authorized himself
                if (User.mailChanged(edt.getMail(), usr.getId()))
                { // the mailaddress changed
                    usr.setMail(edt.getMail());
                }
                // update the fore- and surname
                usr.setForename(edt.getForename());
                usr.setSurname(edt.getSurName());
                if (!(pw1 == null) && !(pw2 == null))
                {
                    if (!(pw2.isEmpty()) && !(pw1.isEmpty()))
                    { // new password has been entered
                        if (pw1.equals(pw2))
                        { // the repetition is equal to the new pw
                            if (pw1.length() < xcmConf.PW_LEN)
                            {
                                Object[] o = new Object[]
                                    {
                                        xcmConf.PW_LEN.toString()
                                    };
                                Optional<String> opt = Optional.of(context.getAcceptLanguage());
                                String shortPw = msg.get("i18nMsg_ShortPw", opt, o).get();
                                context.getFlashCookie().error(shortPw, (Object) null);
                                edt.setPw("");
                                edt.setPwn1("");
                                edt.setPwn2("");

                                return Results.html().template("/views/UserHandler/editUserForm.ftl.html").render(edt);
                            }

                            usr.hashPasswd(pw2);
                        }
                        else
                        { // the passwords are not equal
                            context.getFlashCookie().error("i18nMsg_WrongPw", (Object) null);
                            edt.setPw("");
                            edt.setPwn1("");
                            edt.setPwn2("");
                            return Results.html().template("/views/UserHandler/editUserForm.ftl.html").render(edt);
                        }
                    }
                }
                Result result = Results.redirect("/user/edit");
                if (Arrays.asList(xcmConf.APP_LANGS).contains(edt.getLanguage()))
                {
                    usr.setLanguage(edt.getLanguage());
                    lang.setLanguage(edt.getLanguage(), result);
                }
                // update the user
                usr.update();
                mcsh.set(context.getSessionCookie().getId(), xcmConf.C_EXPIRA, usr);

                context.getFlashCookie().success("i18nMsg_ChOk", (Object) null);
                return result;
            }
            else
            { // the authorization-prozess failed
                edt.setPw("");
                edt.setPwn1("");
                edt.setPwn2("");
                context.getFlashCookie().error("i18nMsg_FormErr", (Object) null);
                return Results.redirect("/user/edit");
            }
        }
    }

    /**
     * Prepopulates the EditForm and show it <br/>
     * GET /user/edit
     * 
     * @param context
     *            the Context of this Request
     * @return the {@link User}-Edit-Form
     */

    public Result editUserForm(Context context)
    {
        User usr = (User) mcsh.get(context.getSessionCookie().getId());
        if (usr.getLanguage() == null || usr.getLanguage() == "")
        {
            Optional<Result> opt = null;
            usr.setLanguage(lang.getLanguage(context, opt).get());
            usr.update();
            mcsh.set(context.getSessionCookie().getId(), xcmConf.C_EXPIRA, usr);
        }
        return Results.html().render(EditUsr.prepopulate(usr));
    }

}
