package com.sid.projects;

import com.DeathByCaptcha.Captcha;
import com.DeathByCaptcha.Client;
import com.DeathByCaptcha.Exception;
import com.DeathByCaptcha.SocketClient;
import com.sid.projects.exceptions.DbcClientException;
import com.sid.projects.exceptions.UnsolvedCaptcha;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class ImageCaptchaSolver
{
    private Client client;
    private Captcha captcha;

    private String captchaFilePath;
    private Pattern acceptedPattern;

    private int solveAttemptCount;
    private int maximumSolveAttempts;

    private int incorrectAttemptCount = 0;
    private static final int maximumIncorrectAttempts = 3;


    public ImageCaptchaSolver(String captchaFilePath, Pattern acceptedPattern, int maximumSolveAttempts) {
        this.captchaFilePath = captchaFilePath;
        this.acceptedPattern = acceptedPattern;
        this.solveAttemptCount = 0;
        this.maximumSolveAttempts = maximumSolveAttempts;

    }

    private ImageCaptchaSolver validateCredentials() throws IOException, Exception
    {
        client = new SocketClient(System.getProperty("DBCUser"), System.getProperty("DBCPass"));
        double clientBalance = client.getBalance();

        return this;
    }

    public String resolveCaptcha() throws UnsolvedCaptcha, DbcClientException
    {
        // (?<!\S)[1-9](?!\S)

        try
        {
            captcha = client.decode(new File(captchaFilePath));
        } catch (IOException | Exception | InterruptedException e)
        {
            e.printStackTrace();
            throw new DbcClientException("An error occurred during connection");
        } finally
        {
            solveAttemptCount++;
        }

        if (captcha != null && captcha.text != null && captcha.text.matches(acceptedPattern.pattern()))
        {
            return captcha.text;
        }
        else
        {
            reportUnsolvedCaptcha();
            if (solveAttemptCount < maximumSolveAttempts) resolveCaptcha(); // Recursive
            else throw new UnsolvedCaptcha("Unable to resolve captcha");

        }
        return ""; // Shouldn't ever happen right??
    }

    private void reportUnsolvedCaptcha() {
        try
        {
            client.report(captcha);
        } catch (IOException | Exception e)
        {
            e.printStackTrace();
        }
    }

    public void reportIncorrectCaptcha() {
        incorrectAttemptCount++;
        reportUnsolvedCaptcha();
    }

    public void reportCorrectCaptcha() throws IOException
    {
        Files.deleteIfExists(new File(captchaFilePath).toPath());
    }
}
