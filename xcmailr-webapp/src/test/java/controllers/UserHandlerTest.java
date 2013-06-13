package controllers;

import static org.junit.Assert.assertTrue;
import java.util.Map;

import models.User;
import ninja.NinjaTest;
import ninja.utils.NinjaProperties;
import ninja.utils.NinjaTestServer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

public class UserHandlerTest extends NinjaTest
{

    @Inject
    NinjaProperties ninjaProp;

    Map<String, String> headers = Maps.newHashMap();

    Map<String, String> formParams = Maps.newHashMap();

    Map<String, String> returnedData = Maps.newHashMap();

    Map<String, String> userData = Maps.newHashMap();

    String result;

    private static NinjaTestServer ninjaTestServer;

    @BeforeClass
    public static void beforeClass()
    {
        ninjaTestServer = new NinjaTestServer();

    }

    @AfterClass
    public static void afterClass()
    {
        ninjaTestServer.shutdown();
    }

    @Before
    public void setUp()
    {
        formParams.clear();
        headers.clear();
        returnedData.clear();

        User u = new User("John", "Doe", "admin@localhost.test", "1234", "en");
        u.setActive(true);
        u.save();

        userData.put("firstName", "John");
        userData.put("surName", "Doe");
        userData.put("mail", "admin@localhost.test");
        userData.put("language", "en");
        formParams.clear();
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/login", headers, formParams);
        formParams.clear();
    }

    @After
    public void tearDown()
    {

    }

    @Test
    public void testUserEditing()
    {

        /*
         * TEST: Set no firstName
         */
        formParams.clear();
        formParams.put("firstName", "");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        System.out.println(result+"\n\n\n");
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: Set no surname
         */
        formParams.clear();
        formParams.put("firstName", "John");
        formParams.put("surName", "");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: Set a wrong formatted mail
         */
        formParams.clear();
        formParams.put("firstName", "John");
        formParams.put("surName", "Doe");
        formParams.put("mail", "@this.de");
        formParams.put("password", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: Set a wrong formatted mail, again
         */
        formParams.clear();
        formParams.put("firstName", "John");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin.this.de");
        formParams.put("password", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: set no passwd
         */
        formParams.clear();
        formParams.put("firstName", "Johnny");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: set wrong passwd (reversed order)
         */
        formParams.clear();
        formParams.put("firstName", "Johnny");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "4321");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: set wrong passwd (completely different chars & length)
         */
        formParams.clear();
        formParams.put("firstName", "Johnny");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "abcdef");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);

        returnedData = HtmlUtils.readInputFormData(result);
        // check that the user-data-edit had failed
        assertTrue(result.contains("class=\"error\">"));

        // the returned data should (in cause of the error) now be equal to the (unchanged)userdata
        TestUtils.testMapEntryEquality(userData, returnedData);

        /*
         * TEST: Edit the Userdata correctly (fore- and surname only)
         */
        formParams.clear();
        formParams.put("firstName", "Johnny");
        formParams.put("surName", "Doey");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);

        // check if the userdata-edit has been successfully changed
        assertTrue(result.contains("class=\"success\">"));

        // the returned data should now be equal to the formparams without the password
        formParams.remove("password");

        TestUtils.testMapEntryEquality(formParams, returnedData);

        /*
         * TEST: Edit the Userdata correctly (fore- and surname and passwords)
         */
        formParams.clear();
        formParams.put("firstName", "John");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        formParams.put("passwordNew1", "4321");
        formParams.put("passwordNew2", "4321");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);

        // check if the userdata-edit has been successfully changed
        assertTrue(result.contains("class=\"success\">"));

        // the returned data should now be equal to the formparams without the password
        formParams.remove("password");
        formParams.remove("passwordNew1");
        formParams.remove("passwordNew2");


        TestUtils.testMapEntryEquality(formParams, returnedData);

    }

    @Test
    public void testUserWrongNewPws()
    {
        /*
         * TEST: Edit the Userdata with two new passwords which were not equal
         */
        formParams.clear();
        formParams.put("firstName", "John");
        formParams.put("surName", "Doe");
        formParams.put("mail", "admin@localhost.test");
        formParams.put("password", "1234");
        formParams.put("passwordNew1", "4321");
        formParams.put("passwordNew2", "1234");
        formParams.put("language", "en");
        result = ninjaTestBrowser.makePostRequestWithFormParameters(getServerAddress() + "/user/edit", headers,
                                                                    formParams);
        returnedData = HtmlUtils.readInputFormData(result);

        // check if the userdata-edit has been successfully changed

        assertTrue(result.contains("class=\"error\">"));
        System.out.println(returnedData + "\n\n\n");
        // the returned data should now be equal to the formparams without the password
        formParams.remove("password");
        formParams.remove("passwordNew1");
        formParams.remove("passwordNew2");

        TestUtils.testMapEntryEquality(formParams, returnedData);
    }

}
