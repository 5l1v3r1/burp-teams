package burp;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.apache.commons.codec.digest.DigestUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;

public class BurpExtender implements IBurpExtender, ITab, IExtensionStateListener {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private PrintWriter stderr;
    private PrintWriter stdout;
    private JPanel panel;
    private java.util.List<String> NATIVE_LOOK_AND_FEELS = Arrays.asList("GTK","Windows","Aqua");
    private List<String> DARK_THEMES = Arrays.asList("Darcula");
    private boolean isNativeTheme;
    private boolean isDarkTheme;
    private Socket socket = null;
    private JLabel status;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel teamStatus;
    private JButton createTeamBtn;
    private JTextField newTeamName;
    private JButton joinTeamBtn;
    private JTextField joinTeamIDText;
    private JTextField joinTeamName;
    private TreeMap<String, String> myTeams = new TreeMap<>();
    private JComboBox myTeamsCombo;
    private JLabel myTeamIDLabel;

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
        helpers = callbacks.getHelpers();
        stderr = new PrintWriter(callbacks.getStderr(), true);
        stdout = new PrintWriter(callbacks.getStdout(), true);
        this.callbacks = callbacks;
        callbacks.setExtensionName("Burp Teams");
        callbacks.registerExtensionStateListener(this);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                stdout.println("Burp Teams v0.1");
                isNativeTheme = NATIVE_LOOK_AND_FEELS.contains(UIManager.getLookAndFeel().getID());
                isDarkTheme = DARK_THEMES.contains(UIManager.getLookAndFeel().getID());
                panel = new JPanel();
                panel.setLayout(new FlowLayout());
                //panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                GridLayout configureGrid = new GridLayout(4, 2);
                JPanel configurePanel = new JPanel(configureGrid);
                JLabel serverAddressLabel = new JLabel("Server address");
                serverAddressLabel.setPreferredSize(new Dimension(200, 25));
                JTextField serverAddress = new JTextField();
                serverAddress.setPreferredSize(new Dimension(200, 25));
                serverAddress.setText("http://localhost:3000");
                configurePanel.add(serverAddressLabel);
                configurePanel.add(serverAddress);
                JLabel nameLabel = new JLabel("Your name");
                nameLabel.setPreferredSize(new Dimension(200, 25));
                JTextField name = new JTextField();
                name.setPreferredSize(new Dimension(200, 25));
                configurePanel.add(nameLabel);
                configurePanel.add(name);
                JPanel configureFieldset = new JPanel();
                connectBtn = new JButton("Connect");
                disconnectBtn = new JButton("Disconnect");
                status = new JLabel("Disconnected.");
                disconnectBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        disconnect();
                    }
                });
                if(!isDarkTheme && !isNativeTheme) {
                    disconnectBtn.setBackground(Color.decode("#000000"));
                    disconnectBtn.setForeground(Color.white);
                }
                disconnectBtn.setEnabled(false);
                configurePanel.add(disconnectBtn);
                connectBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if(name.getText().length() == 0) {
                            status.setText("Please enter a name");
                            return;
                        }
                        try {
                            IO.Options opts = new IO.Options();
                            opts.forceNew = true;
                            opts.reconnection = false;
                            socket = IO.socket(serverAddress.getText(), opts);
                        } catch (URISyntaxException err) {
                            stderr.println("Error invalid address:"+err.toString());
                            status.setText("Error invalid address:"+err.toString());
                            return;
                        }
                        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                connect();
                                if(socket.connected()) {
                                    socket.emit("set name", name.getText());
                                }
                            }

                        }).on("event", new Emitter.Listener() {

                            @Override
                            public void call(Object... args) {}

                        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                            @Override
                            public void call(Object... args) {

                            }

                        }).on(Socket.EVENT_ERROR, new Emitter.Listener() {
                            @Override
                            public void call(Object... objects) {
                                disconnect();
                                status.setText("Error. Disconnected.");
                            }
                        });
                        socket.connect();
                    }
                });
                if(!isDarkTheme && !isNativeTheme) {
                    connectBtn.setBackground(Color.decode("#005a70"));
                    connectBtn.setForeground(Color.white);
                }
                configurePanel.add(connectBtn);
                JLabel statusLabel = new JLabel("Status");
                configurePanel.add(statusLabel);
                configurePanel.add(status);
                configureFieldset.add(configurePanel);
                configureFieldset.setBorder(BorderFactory.createTitledBorder("Configure"));
                configureFieldset.setPreferredSize(new Dimension(600, 170));
                panel.add(configureFieldset);
                GridLayout teamGrid = new GridLayout(3, 2);
                JPanel teamPanel = new JPanel(teamGrid);
                JPanel createTeamFieldset = new JPanel();
                createTeamFieldset.setBorder(BorderFactory.createTitledBorder("Create Team"));
                createTeamFieldset.setPreferredSize(new Dimension(600, 170));
                JLabel teamNameLabel = new JLabel("Team name");
                teamNameLabel.setPreferredSize(new Dimension(200, 25));
                newTeamName = new JTextField();
                newTeamName.setEnabled(false);
                newTeamName.setPreferredSize(new Dimension(200, 25));
                teamPanel.add(teamNameLabel);
                teamPanel.add(newTeamName);
                createTeamBtn = new JButton("Create team");
                createTeamBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if(newTeamName.getText().length() == 0) {
                            teamStatus.setText("Please enter a team name");
                            return;
                        }
                        String teamKey = generateTeamKey();
                        String teamName = newTeamName.getText();
                        teamStatus.setText("Copied team ID to clipboard and joined");
                        newTeamName.setText("");
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(new StringSelection(teamKey), null);
                        joinTeam(teamName, teamKey);
                    }
                });
                createTeamBtn.setEnabled(false);
                if(!isDarkTheme && !isNativeTheme) {
                    createTeamBtn.setBackground(Color.decode("#005a70"));
                    createTeamBtn.setForeground(Color.white);
                }

                teamPanel.add(new JLabel());
                teamPanel.add(createTeamBtn);
                teamStatus = new JLabel();
                teamPanel.add(new JLabel());
                teamPanel.add(teamStatus);
                createTeamFieldset.add(teamPanel);
                panel.add(createTeamFieldset);


                GridLayout myTeamGrid = new GridLayout(2,2);
                JPanel myTeamPanel = new JPanel(myTeamGrid);
                JPanel myTeamFieldset = new JPanel();
                myTeamFieldset.setBorder(BorderFactory.createTitledBorder("My Teams"));
                myTeamFieldset.setPreferredSize(new Dimension(600, 170));
                myTeamIDLabel = new JLabel();
                myTeamIDLabel.setPreferredSize(new Dimension(200, 25));
                JLabel myTeamLabel = new JLabel("My teams");
                myTeamsCombo = new JComboBox();
                myTeamsCombo.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String team = myTeamsCombo.getSelectedItem().toString();
                        myTeamIDLabel.setText(myTeams.get(team));
                    }
                });
                updateMyTeams();


                myTeamPanel.add(myTeamLabel);
                myTeamPanel.add(myTeamsCombo);
                myTeamPanel.add(myTeamIDLabel);
                JButton copyTeamIDBtn = new JButton("Copy team ID");
                copyTeamIDBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String team = myTeamsCombo.getSelectedItem().toString();
                        String teamKey = myTeams.get(team);
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(new StringSelection(teamKey), null);
                    }
                });
                if(!isDarkTheme && !isNativeTheme) {
                    copyTeamIDBtn.setBackground(Color.decode("#005a70"));
                    copyTeamIDBtn.setForeground(Color.white);
                }
                myTeamPanel.add(copyTeamIDBtn);
                myTeamFieldset.add(myTeamPanel);
                panel.add(myTeamFieldset);

                GridLayout joinTeamGrid = new GridLayout(4, 2);
                JPanel joinTeamPanel = new JPanel(joinTeamGrid);
                JPanel joinTeamFieldset = new JPanel();
                joinTeamFieldset.setBorder(BorderFactory.createTitledBorder("Join Team"));
                joinTeamFieldset.setPreferredSize(new Dimension(600, 170));
                JLabel joinTeamNameLabel = new JLabel("Team name");
                joinTeamNameLabel.setPreferredSize(new Dimension(200, 25));
                joinTeamName = new JTextField();
                joinTeamName.setEnabled(false);
                joinTeamName.setPreferredSize(new Dimension(200, 25));
                joinTeamPanel.add(joinTeamNameLabel);
                joinTeamPanel.add(joinTeamName);

                JLabel teamIDLabel = new JLabel("Team ID");
                teamIDLabel.setPreferredSize(new Dimension(200, 25));
                joinTeamIDText = new JTextField();
                joinTeamIDText.setEnabled(false);
                joinTeamIDText.setPreferredSize(new Dimension(200, 25));
                joinTeamPanel.add(teamIDLabel);
                joinTeamPanel.add(joinTeamIDText);

                joinTeamBtn = new JButton("Join team");
                JLabel joinTeamStatus = new JLabel();
                joinTeamBtn.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if(joinTeamName.getText().length() == 0) {
                            joinTeamStatus.setText("Please enter a team name");
                            return;
                        }
                        String teamKey = joinTeamIDText.getText();
                        String teamName = newTeamName.getText().trim();
                        if(teamKey.length() != 64) {
                            joinTeamStatus.setText("Invalid team ID");
                            return;
                        }
                        joinTeam(teamName, teamKey);
                    }
                });
                joinTeamBtn.setEnabled(false);
                if(!isDarkTheme && !isNativeTheme) {
                    joinTeamBtn.setBackground(Color.decode("#005a70"));
                    joinTeamBtn.setForeground(Color.white);
                }
                joinTeamPanel.add(new JLabel());
                joinTeamPanel.add(joinTeamBtn);
                joinTeamPanel.add(new JLabel());
                joinTeamPanel.add(joinTeamStatus);
                joinTeamFieldset.add(joinTeamPanel);
                panel.add(joinTeamFieldset);

                callbacks.addSuiteTab(BurpExtender.this);
            }
        });
    }
    private void updateMyTeams() {
        myTeamsCombo.removeAllItems();
        for(Map.Entry<String, String> e : myTeams.entrySet()) {
            String teamName = e.getKey();
            String teamID = e.getValue();
            myTeamsCombo.addItem(teamName);
        }
    }
    public void joinTeam(String teamName, String teamID) {
        if(socket.connected()) {
            socket.emit("subscribe", teamID, teamName);
            myTeams.put(teamName, teamID);
            updateMyTeams();
        } else {
            teamStatus.setText("Unable to join team. Not connected.");
        }
    }
    public void connect() {
        status.setText("Connected.");
        connectBtn.setEnabled(false);
        disconnectBtn.setEnabled(true);
        createTeamBtn.setEnabled(true);
        newTeamName.setEnabled(true);
        joinTeamIDText.setEnabled(true);
        joinTeamBtn.setEnabled(true);
        joinTeamName.setEnabled(true);
        stdout.println("Connected.");
    }

    public void disconnect() {
        socket.disconnect();
        status.setText("Disconnected.");
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        createTeamBtn.setEnabled(false);
        newTeamName.setEnabled(false);
        joinTeamIDText.setEnabled(false);
        joinTeamBtn.setEnabled(false);
        joinTeamName.setEnabled(false);
        stdout.println("Disconnected.");
    }

    private String generateTeamKey() {
        byte[] randomBytes = new byte[256];
        SecureRandom secureRandom = null;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            stderr.println("Error get algo:"+e.toString());
            return null;
        }
        secureRandom.nextBytes(randomBytes);
        return DigestUtils.sha256Hex(helpers.bytesToString(randomBytes));
    }

    @Override
    public String getTabCaption() {
        return "Teams";
    }

    @Override
    public Component getUiComponent()
    {
        return panel;
    }

    @Override
    public void extensionUnloaded() {
        stdout.println("Burp teams unloaded");
        if(socket != null && socket.connected()) {
            disconnect();
        }
    }


}
