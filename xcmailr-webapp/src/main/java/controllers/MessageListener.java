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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.subethamail.smtp.helper.SimpleMessageListener;

import com.avaje.ebean.Ebean;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import conf.XCMailrConf;
import etc.MessageComposer;
import models.MBox;
import models.Mail;
import models.MailTransaction;
import models.User;

/**
 * Handles all Actions for incoming Mails
 * 
 * @author Patrick Thum, Xceptance Software Technologies GmbH, Germany
 */
@Singleton
public class MessageListener implements SimpleMessageListener
{
    @Inject
    XCMailrConf xcmConfiguration;

    @Inject
    MailrMessageSenderFactory mailrSenderFactory;

    @Inject
    JobController jobController;

    @Inject
    Logger log;

    static final String LOOP_HEADER_NAME = "X-Loop";

    static final String LOOP_HEADER_VALUE_PREFIX = "loopbreaker";

    @Override
    public boolean accept(String from, String recipient)
    {
        // accept the address if the domain is contained in the application.conf
        String[] splitaddress = recipient.split("@");

        List<String> domainlist = Arrays.asList(xcmConfiguration.DOMAIN_LIST);

        if ((splitaddress.length == 2) && (domainlist.contains(splitaddress[1])))
            return true;

        // the mailaddress has a strange form or has an recipient with a domain-part that does not belong to our
        // domains
        // log status 500 (relay denied)
        if (xcmConfiguration.MTX_MAX_AGE != 0)
        { // if mailtransaction.maxage is set to 0 -> log nothing
            MailTransaction mtx = new MailTransaction(500, from, null, recipient);
            jobController.mtxQueue.add(mtx);
        }
        return false;
    }

    /**
     * Checks if forwarding the mail could trigger a loop.
     * <ul>
     * <li>checks if the Return-Path header is empty</li>
     * <li>checks if the References / In-Reply-To Header include a mail from this domain</li>
     * <li>checks if the custom X-Loop header exists and checks its content</li>
     * </ul>
     * The number of hops / too many Received fields is checked by an internal library.
     * 
     * @param mail
     *            the mail to check
     * @return null if there was no loop, an error message if there was
     * @throws MessagingException
     */
    String checkForLoop(MimeMessage mail) throws MessagingException
    {
        String errorMessage;

        // TODO: 2018-10-11: return path header isn't send by default (thunderbird does, but gmail web doesn't)
        // disabled code until fixed
        // check the first Return-Path header
        // String[] returnPathHeaders = mail.getHeader("Return-Path");
        //
        // if (returnPathHeaders != null)
        // {
        // String returnPathHeader = returnPathHeaders[0];
        // if (returnPathHeader.equals("") || returnPathHeader.equals("<>") || returnPathHeader.equals("< >"))
        // {
        // // loop detected;
        // errorMessage = "Return-Path is empty";
        // return errorMessage;
        // }
        // }
        // else
        // {
        // errorMessage = "Don't forward mails without a return path header";
        // return errorMessage;
        // }

        // check custom X-Loop header
        String customHeader = mail.getHeader(LOOP_HEADER_NAME, "###");
        if (customHeader != null)
        {
            customHeader = customHeader.toLowerCase();
            String shouldBeContent = LOOP_HEADER_VALUE_PREFIX + mail.getRecipients(RecipientType.TO)[0];
            if (customHeader.contains(shouldBeContent))
            {
                // loop detected;
                errorMessage = "X-Loop header with this email adress present";
                return errorMessage;
            }
        }

        // determine domain from message ID
        String id = mail.getMessageID();
        if (id != null)
        {
            String[] splitString = id.split("@");

            if (id.length() >= 2)
            {
                String domain = splitString[1];
                domain = domain.toLowerCase();

                // check References header
                String referenceHeaders = mail.getHeader("References", "###");
                if (referenceHeaders != null)
                {
                    referenceHeaders = referenceHeaders.toLowerCase();
                    if (referenceHeaders.contains("@" + domain))
                    {
                        // loop detected;
                        errorMessage = "References header references the domain of this email adress: " + domain;
                        return errorMessage;
                    }
                }

                // check In-Reply-To header
                String inReplyToHeader = mail.getHeader("In-Reply-To", "###");
                if (inReplyToHeader != null)
                {
                    inReplyToHeader = inReplyToHeader.toLowerCase();
                    if (inReplyToHeader.contains("@" + domain))
                    {
                        // loop detected;
                        errorMessage = "In-Reply-To header mentions the domain of this email adress: " + domain;
                        return errorMessage;
                    }
                }
            }
            else
            {
                errorMessage = "Don't forward mails without a normal message id";
                return errorMessage;
            }
        }
        else
        {
            errorMessage = "Don't forward mails without message id";
            return errorMessage;
        }

        return null;
    }

    /**
     * Checks preconditions related to the MBox, such as:
     * <ul>
     * <li>malformed recipient address</li>
     * <li>not existing {@link MBox}</li>
     * <li>disabled {@link MBox}</li>
     * <li>disabled {@link User}</li>
     * </ul>
     * 
     * @param from
     *            the from address
     * @param recipient
     *            the recipient
     * @return the linked {@link MBox} for that recipient address, or null if any precondition failed
     */
    protected MBox doMboxPreconditionChecks(final String from, final String recipient)
    {
        final String[] splitAddress;
        final MBox mailBox;

        splitAddress = recipient.split("@");

        if (splitAddress.length != 2)
        { // the mail-address does not have the expected pattern -> do nothing, just log it
            createMtxAndAddToQueue(0, from, null, recipient);
            return null;
        }

        if (MBox.mailExists(splitAddress[0], splitAddress[1]) == false)
        { // mailaddress/forward does not exist
            createMtxAndAddToQueue(100, from, recipient, null);
            return null;
        }
        mailBox = MBox.getByName(splitAddress[0], splitAddress[1]);
        final String forwardTarget = (mailBox.getUsr() != null) ? mailBox.getUsr().getMail() : "";

        if (mailBox.isActive() == false)
        { // there's a mailaddress, but the forward is inactive
            createMtxAndAddToQueue(200, from, recipient, forwardTarget);
            mailBox.increaseSup();
            return null;
        }
        if (mailBox.getUsr() == null || mailBox.getUsr().isActive() == false)
        { // either the user does not exist or the user is set to inactive
            createMtxAndAddToQueue(600, from, recipient, forwardTarget);
            mailBox.increaseSup();
            return null;
        }
        return mailBox;
    }

    /**
     * @param from
     * @param recipient
     * @param data
     */
    @Override
    public void deliver(String from, String recipient, InputStream data)
    {
        try
        {
            final MBox mailBox = doMboxPreconditionChecks(from, recipient);

            if (mailBox == null)
            {
                return;
            }

            final Address forwardAddress;
            final String forwardTarget = (mailBox.getUsr() != null) ? mailBox.getUsr().getMail() : "";

            final Session session = mailrSenderFactory.getSession();
            session.setDebug(xcmConfiguration.OUT_SMTP_DEBUG);

            String rawContent = null;
            try
            {
                rawContent = readLimitedAmount(data, xcmConfiguration.MAX_MAIL_SIZE);
            }
            catch (IOException e)
            {
                if (e instanceof SizeLimitExceededException)
                {
                    log.error("Dropped mail '{} => {}' since its size exceed configured limit of {} bytes", new Object[]
                        {
                          from, recipient, Integer.toString(xcmConfiguration.MAX_MAIL_SIZE)
                        });
                    return;
                }
                throw e;
            }

            MimeMessage mail = new MimeMessage(session, new ByteArrayInputStream(rawContent.getBytes()));

            // write to mail table
            persistMail(mailBox, from, mail, rawContent);

            // check if the mail address is configured to forward emails
            // the mail is still persisted (see above)
            if (!mailBox.isForwardEmails())
                return;

            // check for a possible loop ...
            String loopError = checkForLoop(mail);
            if (loopError != null)
            {
                log.info("Broke a possible loop");
                log.info("Email was not forwarded");
                log.info("From: " + from + " To:" + recipient);
                log.info(loopError);
                return;
            }
            // there's an existing and active mail-address
            // add the target-address to the list
            try
            {
                forwardAddress = new InternetAddress(forwardTarget);
                // rewrite the message body and wrap the original message in a new one if mail.msg.rewrite is
                // set to true
                if (xcmConfiguration.MSG_REWRITE)
                {
                    mail = MessageComposer.createQuotedMessage(mail);
                }
                mail.setRecipient(Message.RecipientType.TO, forwardAddress);
                mail.removeHeader("Cc");
                mail.removeHeader("BCC");

                mail.setSender(new InternetAddress(recipient));
                mail.setFrom(new InternetAddress(recipient));

                // intention: set 'from' to the incoming email address, set the sender to xcmailers one
                // for clarity. Unfortunately it doesn't work because the SMTP server refuses to send these mails
                // mail.setFrom(new InternetAddress(from));

                // set the Reply-To header to the incoming email address, the semantic one of the original sender
                mail.setReplyTo(InternetAddress.parse(from));
                mail.addHeader("X-FORWARDED-FROM", from);

                // Set headers to break loops
                String loopHeaderContent = LOOP_HEADER_VALUE_PREFIX + recipient;
                mail.addHeader(LOOP_HEADER_NAME, loopHeaderContent);
                mail.addHeader("Auto-Submitted", "auto-forwarded");

                // send the mail in a separate thread
                MailrMessageSenderFactory.ThreadedMailSend tms = mailrSenderFactory.new ThreadedMailSend(mail, mailBox);
                tms.start();
            }
            catch (AddressException e)
            {
                log.error(e.getMessage());
                // the message can't be forwarded (has not the correct format)
                // this SHOULD never be the case...
                createMtxAndAddToQueue(400, from, recipient, forwardTarget);
            }
            catch (IOException e)
            {
                log.error(e.getMessage());
                // the message can't be forwarded (has not the correct format)
                // this SHOULD never be the case...
                createMtxAndAddToQueue(400, from, recipient, forwardTarget);
            }
        }
        catch (MessagingException e)
        {
            // the message-creation-process failed
            // either the session can't be created or the input-stream was wrong
            log.error(e.getMessage());
        }
        catch (IOException e)
        {
            log.error(e.getMessage());
        }
    }

    /**
     * Reads up to maxSize characters from data input stream using 7-bit ASCII encoding. If the limit is exceeded an
     * {@link SizeLimitExceededException} is thrown.
     * 
     * @param data
     *            an {@link InputStream}
     * @param maxSize
     *            determines the maximum amount of characters to be read from data
     * @return the streams' data
     * @throws SizeLimitExceededException
     *             if maxSize read limit is exceeded
     * @throws IOException if an I/O error occurred
     */
    static String readLimitedAmount(InputStream data, int maxSize) throws IOException
    {
        // StringBuilderWriter doesn't need to be closed since the close method is no-op
        @SuppressWarnings("resource")
        final StringBuilderWriter sw = new StringBuilderWriter();

        int n;
        long count = 0;
        char[] buffer = new char[4096];
        final InputStreamReader in = new InputStreamReader(data, StandardCharsets.US_ASCII);
        while (IOUtils.EOF != (n = in.read(buffer)))
        {
            sw.write(buffer, 0, n);
            count += n;
            if (count > maxSize)
            {
                throw new SizeLimitExceededException("Data stream exceeds size limit of " + maxSize + " bytes");
            }
        }

        return sw.toString();
    }

    private void persistMail(MBox mailBox, String from, MimeMessage mail, String rawMessage)
        throws MessagingException, IOException
    {
        Mail newMail = new Mail();
        newMail.setMailbox(mailBox);
        newMail.setSender(from);
        newMail.setSubject(StringUtils.defaultString(mail.getSubject()));
        newMail.setMessage(rawMessage);
        newMail.setReceiveTime(System.currentTimeMillis());
        newMail.setUuid(UUID.randomUUID().toString());

        Ebean.save(newMail);
    }

    private void createMtxAndAddToQueue(final int status, final String from, final String recipient,
                                        final String forwardTarget)
    {
        if (xcmConfiguration.MTX_MAX_AGE != 0)
        {// if mailtransaction.maxage is set to 0 -> log nothing
            final MailTransaction mtx = new MailTransaction(status, from, recipient, forwardTarget);
            jobController.mtxQueue.add(mtx);
        }
    }

    public static class SizeLimitExceededException extends IOException
    {

        public SizeLimitExceededException()
        {
            super();
        }

        public SizeLimitExceededException(String arg0, Throwable arg1)
        {
            super(arg0, arg1);
        }

        public SizeLimitExceededException(String arg0)
        {
            super(arg0);
        }

        public SizeLimitExceededException(Throwable arg0)
        {
            super(arg0);
        }

    }
}
