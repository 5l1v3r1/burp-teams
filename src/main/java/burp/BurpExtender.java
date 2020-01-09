package burp;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;

public class BurpExtender implements IBurpExtender, ITab{
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private PrintWriter stderr;
    private PrintWriter stdout;
    private JPanel panel;

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
        helpers = callbacks.getHelpers();
        stderr = new PrintWriter(callbacks.getStderr(), true);
        stdout = new PrintWriter(callbacks.getStdout(), true);
        this.callbacks = callbacks;
        callbacks.setExtensionName("Burp Teams");
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                stdout.println("Burp Teams v0.1");
            }
        });
    }

    @Override
    public String getTabCaption() {
        return "Burp Teams";
    }

    @Override
    public Component getUiComponent()
    {
        return panel;
    }
}
