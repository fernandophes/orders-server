package br.edu.ufersa.cc.sd;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.services.LocalizationServer;
import br.edu.ufersa.cc.sd.services.OrderService;
import br.edu.ufersa.cc.sd.services.ProxyServer;
import br.edu.ufersa.cc.sd.utils.Constants;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.services.AbstractServer;
import br.edu.ufersa.cc.sd.services.ApplicationServer;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class.getSimpleName());

    private static JPanel box = new JPanel();
    private static final JPanel SERVERS_GRID = new JPanel();
    private static final List<AbstractServer> SERVERS = new ArrayList<>();

    public static void main(final String[] args) throws SQLException {
        LOG.info("Inicializando banco de dados...");
        OrderService.initialize();

        openWindow();
    }

    private static void openWindow() {
        // Criar um JFrame
        final var frame = new JFrame("Serviço com os 3 servidores");
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                SERVERS.forEach(AbstractServer::close);
            }
        });

        box.setLayout(new BoxLayout(box, BoxLayout.PAGE_AXIS));

        // Label com o comando
        final var title = new JLabel("Controle os servidores");
        box.add(title);

        // Painel para os botões e labels
        refreshGrid();

        // Adicionar o painel de botões à janela
        box.add(SERVERS_GRID);

        // Label com o comando
        final var replicaTitle = new JLabel("Criar réplicas");
        box.add(replicaTitle);

        /*
         * Área das réplicas
         */
        final var replicaGrid = new JPanel(new GridLayout(1, 3));

        final var replicaLocalizationButton = new JButton("Localização");
        final var replicaProxyButton = new JButton("Proxy");
        final var replicaApplicationButton = new JButton("Aplicação");

        replicaLocalizationButton.addActionListener(e -> addAndStartServer(new LocalizationServer()));
        replicaProxyButton.addActionListener(e -> addAndStartServer(createProxyServer()));
        replicaApplicationButton.addActionListener(e -> addAndStartServer(new ApplicationServer()));

        replicaGrid.add(replicaLocalizationButton);
        replicaGrid.add(replicaProxyButton);
        replicaGrid.add(replicaApplicationButton);

        box.add(replicaGrid);

        // Exibir a janela
        frame.add(box, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static void refreshGrid() {
        SERVERS_GRID.removeAll();
        SERVERS_GRID.setLayout(new GridLayout(SERVERS.size(), 3));
        SERVERS.forEach(server -> addServerToGrid(server, server.getNature().getName()));

        box.updateUI();
    }

    private static void addAndStartServer(final AbstractServer server) {
        SERVERS.add(server);
        server.run();
        refreshGrid();
    }

    private static void addServerToGrid(final AbstractServer server, final String labelText) {
        final var label = new JLabel(labelText, SwingConstants.CENTER);

        final var addressLabel = new JLabel(on(server.getAddress()), SwingConstants.CENTER);
        addressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        final var button = new JButton("Desligar");
        button.addActionListener(e -> stopServer(server));

        SERVERS_GRID.add(label);
        SERVERS_GRID.add(addressLabel);
        SERVERS_GRID.add(button);
    }

    private static void stopServer(final AbstractServer server) {
        server.close();
        SERVERS.remove(server);
        refreshGrid();
    }

    private static String on(final InetSocketAddress address) {
        return "<html><center>Disponível em " + address.getHostString() + ":" + address.getPort() + "</center></html>";
    }

    private static ProxyServer createProxyServer() {
        final var localizationAddress = askForAddress(Nature.LOCALIZATION, "Conectar ao servidor de localização");
        final var applicationAddress = askForAddress(Nature.APPLICATION, "Conectar ao servidor de aplicação");

        return new ProxyServer(localizationAddress, applicationAddress);
    }

    private static InetSocketAddress askForAddress(final Nature nature, final String message) {
        final var localizationServer = SERVERS.stream()
                .dropWhile(server -> server.getNature() != nature)
                .map(AbstractServer::getAddress)
                .findFirst().orElse(new InetSocketAddress(Constants.getDefaultHost(), 0000));
        final var placeholder = localizationServer.getHostString() + ":" + localizationServer.getPort();
        final var optionPane = JOptionPane.showInputDialog(message, placeholder);

        final var divider = optionPane.indexOf(":");
        final var host = optionPane.substring(0, divider);
        final var port = Integer.parseInt(optionPane.substring(divider + 1));
        return new InetSocketAddress(host, port);
    }

}