package br.edu.ufersa.cc.sd;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.services.LocalizationServer;
import br.edu.ufersa.cc.sd.services.OrderService;
import br.edu.ufersa.cc.sd.services.ProxyServer;
import br.edu.ufersa.cc.sd.services.AbstractServer;
import br.edu.ufersa.cc.sd.services.ApplicationServer;

public class Main {

    private static final String OFF = "Desligado";
    private static final String TURN_ON = "Ligar";
    private static final String TURN_OFF = "Desligar";

    private static final Logger LOG = LoggerFactory.getLogger(Main.class.getSimpleName());

    private static final ApplicationServer APPLICATION = new ApplicationServer();
    private static final ProxyServer PROXY = new ProxyServer();
    private static final LocalizationServer LOCALIZATION = new LocalizationServer();

    public static void main(final String[] args) throws SQLException {
        LOG.info("Inicializando banco de dados...");
        OrderService.initialize();

        LOG.info("Inicializando servidor...");
        APPLICATION.run();

        LOG.info("Inicializando servidor de proxy...");
        PROXY.run();

        LOG.info("Inicializando servidor de localização...");
        LOCALIZATION.run();

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
                if (LOCALIZATION.isAlive()) {
                    LOCALIZATION.stop();
                }
                if (PROXY.isAlive()) {
                    PROXY.stop();
                }
                if (APPLICATION.isAlive()) {
                    APPLICATION.stop();
                }
                super.windowClosing(e);
            }
        });

        // Label com o comando
        final var title = new JLabel("Controle os servidores");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        frame.add(title, BorderLayout.NORTH);

        // Painel para os botões e labels
        final var grid = new JPanel(new GridLayout(3, 3));

        // Servidores
        addServer(LOCALIZATION, "Localização", grid);
        final var proxyAddress = addServer(PROXY, "Proxy", grid);
        addServer(APPLICATION, "Aplicação", grid);

        ProxyServer.addListenerWhenChangeAddress(address -> proxyAddress.setText(on(address)));

        // Adicionar o painel de botões à janela
        frame.add(grid, BorderLayout.CENTER);

        // Exibir a janela
        frame.setVisible(true);
    }

    private static JLabel addServer(final AbstractServer server, final String labelText, final JPanel panel) {
        final var label = new JLabel(labelText);
        label.setHorizontalAlignment(SwingConstants.CENTER);

        final var address = new JLabel(on(server.getAddress()));
        address.setHorizontalAlignment(SwingConstants.CENTER);

        final var button = new JButton(TURN_OFF);
        button.addActionListener(e -> switchServerState(server, address, button));

        panel.add(label);
        panel.add(address);
        panel.add(button);

        return address;
    }

    private static void switchServerState(final AbstractServer server, final JLabel statusLabel, final JButton button) {
        if (server.isAlive()) {
            server.stop();
            statusLabel.setText(OFF);
            button.setText(TURN_ON);
        } else {
            server.run();
            statusLabel.setText(on(server.getAddress()));
            button.setText(TURN_OFF);
        }
    }

    private static String on(final InetSocketAddress address) {
        return "<html>Disponível em " + address.getHostString() + ":" + address.getPort() + "</html>";
    }

}