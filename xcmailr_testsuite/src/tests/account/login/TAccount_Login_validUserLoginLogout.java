package tests.account.login;
import org.junit.Test;
import com.xceptance.xlt.api.engine.scripting.AbstractWebDriverScriptTestCase;

import modules.global.headernav.MGlobal_Header_clickSignIn;
import modules.global.headernav.MGlobal_Header_clickSignOut;
import modules.global.headernav.VGlobal_validatePageTitle;
import modules.helper.external.FHelper_getEmailAddressFromExternal;
import modules.helper.global.FHelper_Register_createAccount;
import modules.helper.global.FHelper_Session_flowToStartSession;
import modules.pages.account.login.MAccount_Login_enterClickUserLoginData;
import modules.pages.account.login.VAccount_Login_validatePage;

/**
 * <p>Test login with  valid username and valid password</p>
 * <h1 id="setup-and-preparation">Setup and preparation</h1>
 * <ul>
 * <li>Start session, open homepage and delete all visible cookies</li>
 * <li>Create new account with profile data</li>
 * <li>Click login link in the header and go to the login page</li>
 * </ul>
 * <h1 id="scope">Scope</h1>
 * <ul>
 * <li>Enter valid username, valid password and try to login</li>
 * <li>Validate page title </li>
 * <li>Validate present error message element</li>
 * </ul>
 */
public class TAccount_Login_validUserLoginLogout extends AbstractWebDriverScriptTestCase
{

    /**
     * Executes the test.
     *
     * @throws Throwable if anything went wrong
     */
    @Test
    public void test() throws Throwable
    {
        // -----------------------------------------------
        // # Setup
        // 
        // - Start session
        // -----------------------------------------------
        FHelper_getEmailAddressFromExternal.execute("email_varDynamic");

        FHelper_Session_flowToStartSession.execute();

        // create test account
        FHelper_Register_createAccount.execute("${email_varDynamic}", "password_varDynamic");

        // -----------------------------------------------
        // # Scope

        // -----------------------------------------------
        MGlobal_Header_clickSignIn.execute();

        // validate - Layout
        VAccount_Login_validatePage.execute();

        MAccount_Login_enterClickUserLoginData.execute("${email_varDynamic}", "${password_varDynamic}");

        VGlobal_validatePageTitle.execute("${title_home}");

        MGlobal_Header_clickSignOut.execute();

        VGlobal_validatePageTitle.execute("${title_home}");


    }

}