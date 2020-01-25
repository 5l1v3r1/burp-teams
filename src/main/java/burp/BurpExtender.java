package burp;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;

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

public class BurpExtender implements IBurpExtender, ITab, IExtensionStateListener, IContextMenuFactory {
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
        callbacks.registerContextMenuFactory(this);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final int componentWidth = 220;
                stdout.println("Burp Teams v0.1");
                isNativeTheme = NATIVE_LOOK_AND_FEELS.contains(UIManager.getLookAndFeel().getID());
                isDarkTheme = DARK_THEMES.contains(UIManager.getLookAndFeel().getID());
                panel = new JPanel();
                panel.setLayout(new FlowLayout());
                GridLayout configureGrid = new GridLayout(4, 2);
                JPanel configurePanel = new JPanel(configureGrid);
                JLabel serverAddressLabel = new JLabel("Server address");
                serverAddressLabel.setPreferredSize(new Dimension(componentWidth, 25));
                JTextField serverAddress = new JTextField();
                serverAddress.setPreferredSize(new Dimension(componentWidth, 25));
                serverAddress.setText("http://localhost:3000");
                configurePanel.add(serverAddressLabel);
                configurePanel.add(serverAddress);
                JLabel nameLabel = new JLabel("Your name");
                nameLabel.setPreferredSize(new Dimension(componentWidth, 25));
                JTextField name = new JTextField();
                name.setPreferredSize(new Dimension(componentWidth, 25));
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
                        }).on("call send to repeater", new Emitter.Listener() {
                            @Override

                            public void call(Object... args) {
                                JSONObject obj = (JSONObject)args[0];
                                String teamID = obj.getString("teamID");
                                for(Map.Entry<String, String> entry : myTeams.entrySet()) {
                                    if(entry.getValue().length() != 64 && teamID.length() != 64) {
                                       continue;
                                    }
                                    if (entry.getValue().equals(teamID)) {
                                        byte[] message = (byte[]) args[1];
                                        callbacks.sendToRepeater(obj.getString("host"), obj.getInt("port"), obj.getBoolean("isHttps"), message, obj.getString("caption"));
                                    }
                                }
                            }
                        }).on("call send to intruder", new Emitter.Listener() {
                            @Override

                            public void call(Object... args) {
                                JSONObject obj = (JSONObject)args[0];
                                String teamID = obj.getString("teamID");
                                for(Map.Entry<String, String> entry : myTeams.entrySet()) {
                                    if(entry.getValue().length() != 64 && teamID.length() != 64) {
                                        continue;
                                    }
                                    if (entry.getValue().equals(teamID)) {
                                        byte[] message = (byte[]) args[1];
                                        callbacks.sendToIntruder(obj.getString("host"), obj.getInt("port"), obj.getBoolean("isHttps"), message);
                                    }
                                }
                            }
                        }).on("call send to comparer", new Emitter.Listener() {
                            @Override

                            public void call(Object... args) {
                                JSONObject obj = (JSONObject)args[0];
                                String teamID = obj.getString("teamID");
                                for(Map.Entry<String, String> entry : myTeams.entrySet()) {
                                    if(entry.getValue().length() != 64 && teamID.length() != 64) {
                                        continue;
                                    }
                                    if (entry.getValue().equals(teamID)) {
                                        byte[] message = (byte[]) args[1];
                                        callbacks.sendToComparer(message);
                                    }
                                }
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
                teamNameLabel.setPreferredSize(new Dimension(componentWidth, 25));
                newTeamName = new JTextField();
                newTeamName.setEnabled(false);
                newTeamName.setPreferredSize(new Dimension(componentWidth, 25));
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
                myTeamIDLabel.setPreferredSize(new Dimension(componentWidth, 25));
                JLabel myTeamLabel = new JLabel("My teams");
                myTeamsCombo = new JComboBox();
                myTeamsCombo.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if(myTeamsCombo.getItemCount() > 0 && myTeamsCombo.getSelectedIndex() != 0) {
                            String team = myTeamsCombo.getSelectedItem().toString();
                            myTeamIDLabel.setText(myTeams.get(team));
                        } else if(myTeamsCombo.getItemCount() > 0 && myTeamsCombo.getSelectedIndex() == 0) {
                            myTeamIDLabel.setText("");
                        }
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
                        if(myTeamsCombo.getItemCount() > 0 && myTeamsCombo.getSelectedIndex() != 0) {
                            String team = myTeamsCombo.getSelectedItem().toString();
                            String teamKey = myTeams.get(team);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(new StringSelection(teamKey), null);
                        }
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
                joinTeamNameLabel.setPreferredSize(new Dimension(componentWidth, 25));
                joinTeamName = new JTextField();
                joinTeamName.setEnabled(false);
                joinTeamName.setPreferredSize(new Dimension(componentWidth, 25));
                joinTeamPanel.add(joinTeamNameLabel);
                joinTeamPanel.add(joinTeamName);

                JLabel teamIDLabel = new JLabel("Team ID");
                teamIDLabel.setPreferredSize(new Dimension(componentWidth, 25));
                joinTeamIDText = new JTextField();
                joinTeamIDText.setEnabled(false);
                joinTeamIDText.setPreferredSize(new Dimension(componentWidth, 25));
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
                        String teamName = joinTeamName.getText().trim();
                        if(teamKey.length() != 64) {
                            joinTeamStatus.setText("Invalid team ID");
                            return;
                        }
                        joinTeam(teamName, teamKey);
                        joinTeamStatus.setText("Sucessfully joined team.");
                        joinTeamIDText.setText("");
                        joinTeamName.setText("");
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
        myTeamsCombo.addItem("Please select a team");
        for(Map.Entry<String, String> entry : myTeams.entrySet()) {
            String teamName = entry.getKey();
            String teamID = entry.getValue();
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

    public Emitter getUsers(String teamID) {
        Emitter emitter = socket.emit("get users", teamID);
        return emitter;
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

    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        switch (invocation.getInvocationContext()) {
            case IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST:
            case IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE:
                break;
            default:
                return null;
        }
        List<JMenuItem> menusList = new ArrayList<JMenuItem>();
        byte[] message = invocation.getSelectedMessages()[0].getRequest();
        IHttpService httpService = invocation.getSelectedMessages()[0].getHttpService();
        JMenu menu = new JMenu("Burp teams");
        for(Map.Entry<String, String> entry : myTeams.entrySet()) {
            String teamName = entry.getKey();
            String teamID = entry.getValue();
            JMenu submenu = new JMenu(teamName);
            JMenu sendToRepeaterMenu = new JMenu("Send to Repeater");
            JMenu sendToIntruderMenu = new JMenu("Send to Intruder");
            JMenu sendToComparerMenu = new JMenu("Send to Comparer");
            Emitter emitter = getUsers(teamID);
            emitter.on("return users", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String checkTeamID = args[1].toString();
                    if(!checkTeamID.equals(teamID)) {
                        return;
                    }
                    JMenuItem sendToRepeaterAllUsers = new JMenuItem("All users");
                    sendToRepeaterAllUsers.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String caption = JOptionPane.showInputDialog("Please enter a caption for the repeater tab");
                            socket.emit("send to repeater", teamID, httpService.getHost(), httpService.getPort(), httpService.getProtocol().equals("https"), message, caption, "All users");
                        }
                    });
                    sendToRepeaterMenu.add(sendToRepeaterAllUsers);
                    JSONArray users = (JSONArray) args[0];
                    for(int i=0;i< users.length(); i++) {
                        String user = users.getString(i);
                        JMenuItem userMenuItem = new JMenuItem(user);
                        userMenuItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                String caption = JOptionPane.showInputDialog("Please enter a caption for the repeater tab");
                                socket.emit("send to repeater", teamID, httpService.getHost(), httpService.getPort(), httpService.getProtocol().equals("https"), message, caption, user);
                            }
                        });
                        sendToRepeaterMenu.add(userMenuItem);
                    }

                    JMenuItem sendToIntruderAllUsers = new JMenuItem("All users");
                    sendToIntruderAllUsers.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            socket.emit("send to intruder", teamID, httpService.getHost(), httpService.getPort(), httpService.getProtocol().equals("https"), message, "All users");
                        }
                    });
                    sendToIntruderMenu.add(sendToIntruderAllUsers);
                    for(int i=0;i< users.length(); i++) {
                        String user = users.getString(i);
                        JMenuItem userMenuItem = new JMenuItem(user);
                        userMenuItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                socket.emit("send to intruder", teamID, httpService.getHost(), httpService.getPort(), httpService.getProtocol().equals("https"), message, user);
                            }
                        });
                        sendToIntruderMenu.add(userMenuItem);
                    }

                    JMenuItem sendToComparerAllUsers = new JMenuItem("All users");
                    sendToComparerAllUsers.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            socket.emit("send to comparer", teamID, message, "All users");
                        }
                    });
                    sendToComparerMenu.add(sendToComparerAllUsers);
                    for(int i=0;i< users.length(); i++) {
                        String user = users.getString(i);
                        JMenuItem userMenuItem = new JMenuItem(user);
                        userMenuItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                socket.emit("send to comparer", teamID, message, user);
                            }
                        });
                        sendToComparerMenu.add(userMenuItem);
                    }
                }
            });
            submenu.add(sendToIntruderMenu);
            submenu.add(sendToRepeaterMenu);
            submenu.add(sendToComparerMenu);
            menu.add(submenu);
        }
        menusList.add(menu);
        return menusList;
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
