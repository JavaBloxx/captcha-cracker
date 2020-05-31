package com.sid.projects.torn;

import com.DeathByCaptcha.Captcha;
import com.DeathByCaptcha.Client;
import com.DeathByCaptcha.SocketClient;
import com.sid.projects.ACFBrowser;
import com.sid.projects.torn.exceptions.DbcClientException;
import com.sid.projects.torn.exceptions.UnsolvedCaptcha;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.BrowserType;
import com.teamdev.jxbrowser.chromium.javafx.BrowserView;
import com.teamdev.jxbrowser.chromium.javafx.internal.LightWeightWidget;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class CaptchaSolver
{
    /* Public Methods */
    public static Captcha solveImageCaptcha(String imageUrl, int maxSolveAttempts)
            throws IOException, DbcClientException
    {
        snapshotCaptcha(imageUrl);

        // Sleep for 10 seconds while waiting for the snapshot image to render | Super hacky - fix ploz
        try
        {
            Thread.sleep(10000);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        // Pull image from file
        final File captchaImage = new File("captchaImage.png");

        int solveAttempts = 0;

        final Client client;
        try
        {
            // Establish connection to DBC server
            client = new SocketClient(System.getProperty("DBCUser"), System.getProperty("DBCPass"));

            // Test DBC server connectivity
            double clientBalance = client.getBalance();

            System.out.println("Remaining DBC balance: " + clientBalance);

        } catch (Exception e)
        {
            e.printStackTrace();
            throw new DbcClientException("Could not contact DBC Server");
        }

        Captcha captcha = null;
        try
        {
            // Attempt solving the captcha until server reports captcha as solved.
            boolean captchaIsDecoded = false;
            while (!captchaIsDecoded && solveAttempts < maxSolveAttempts)
            {

                // Send captcha decode request to DBC server
                captcha = client.decode(captchaImage);

                // Log DBC captcha response
                System.out.println("CAPTCHA ID: " + captcha.id);
                System.out.println("CAPTCHA RESPONSE: " + captcha.text);

                /* Check if captcha is solved as a single integer between 1 and 9 */

                if (captcha.isSolved()) // Server reports whether captcha is solved or not
                {
                    Pattern pattern = Pattern.compile("\\d");
                    if (pattern.matcher(captcha.text).matches() && !captcha.text.equals("0") && !captcha.text.isEmpty())
                    // Captcha is a single digit between '1' and '9'.
                    {
                        captchaIsDecoded = true; // Mark captcha as decoded
                    } else
                    {
                        System.out.println("CAPTCHA value is not between the numbers 1 and 9");
                    }
                } else
                {
                    System.out.println("Server reports captcha as unsolved");
                }

                // If captcha hasn't been decoded properly then report it as unsolved
                if (!captchaIsDecoded)
                {
                    // Report captcha as unsolved
                    client.report(captcha);

                    solveAttempts++;
                }

                // If solve attempts have been exceeded throw an exception
                if (solveAttempts >= maxSolveAttempts)
                    throw new UnsolvedCaptcha("Exceeded number of decode attempts on the captcha");
            }
            // Return the captcha answer
            return captcha;
        } catch (Exception ex)
        {
            ex.printStackTrace();
            throw new DbcClientException("Unknown DBC related exception");
        } finally
        {
            client.close();
            Files.deleteIfExists(captchaImage.toPath());
        }
    }

    public static void reportCaptchaUnsolved(Captcha captcha)
    {
        Client client = null;
        try
        {
            client = new SocketClient(System.getProperty("DBCUser"), System.getProperty("DBCPass"));
            client.report(captcha);
        } catch (Exception e)
        {
            e.printStackTrace();
            System.out.println("Failed to verify captcha as solved - Possible malformed page (1)");
        } finally
        {
            if (client != null)
            {
                client.close();
            }
        }
    }

    /* Private Methods */

    private static void snapshotCaptcha(String url)
    {
        // #1 Create Browser instance
        final ACFBrowser browser = new ACFBrowser(BrowserType.LIGHTWEIGHT);
        final BrowserView view = new BrowserView(browser);

        // #2 Set the required view size
        browser.setSize(300, 180);

        // Wait until Chromium resizes view
        try
        {
            Thread.sleep(500);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        // #3 Load web page and wait until web page is loaded completely
        Browser.invokeAndWaitFinishLoadingMainFrame(browser, browser1 -> browser1.loadURL(url));

        Platform.runLater(() ->
        {
            try
            {
                // Wait until Chromium renders web page content
                Thread.sleep(500);

                // #4 Get javafx.scene.image.Image of the loaded web page
                LightWeightWidget lightWeightWidget = (LightWeightWidget) view.getChildren().get(0);
                Image image = lightWeightWidget.getImage();

                // Save the image into a PNG file
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "PNG",
                        new File("captchaImage.png"));
            } catch (Exception ignored)
            {
            } finally
            {
                // Dispose Browser instance
                browser.dispose();
            }
        });

        System.out.println("Done");
    }
}
