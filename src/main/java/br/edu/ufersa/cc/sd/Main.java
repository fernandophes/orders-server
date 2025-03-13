package br.edu.ufersa.cc.sd;

import java.awt.BorderLayout;
import java.awt.GridLayout;
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

import br.edu.ufersa.cc.sd.services.LocalizationService;
import br.edu.ufersa.cc.sd.services.OrderService;
import br.edu.ufersa.cc.sd.services.ProxyService;
import br.edu.ufersa.cc.sd.services.ServerService;
import br.edu.ufersa.cc.sd.utils.Constants;

public class Main {

    private static final String ON = "Disponível em ";
    private static final String OFF = "Desligado";
    private static final String TURN_ON = "Ligar";
    private static final String TURN_OFF = "Desligar";

    private static final Logger LOG = LoggerFactory.getLogger(Main.class.getSimpleName());

    private static final ServerService SERVER = new ServerService();
    private static final ProxyService PROXY = ProxyService.getInstance();
    private static final LocalizationService LOCALIZATION = new LocalizationService();

    public static void main(final String[] args) throws SQLException {
        LOG.info("Inicializando banco de dados...");
        OrderService.initialize();

        LOG.info("Inicializando servidor...");
        SERVER.run();

        LOG.info("Inicializando servidor de proxy...");
        PROXY.run();

        LOG.info("Inicializando servidor de localização...");
        LOCALIZATION.run();

        openHelper();
    }

    private static void openHelper() {
        // Cria um JFrame
        final var janela = new JFrame("Serviço com os 3 servidores");
        janela.setSize(600, 400);
        janela.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        janela.setLayout(new BorderLayout());

        // Label com o comando
        final var labelComando = new JLabel("Controle os servidores");
        labelComando.setHorizontalAlignment(SwingConstants.CENTER);
        janela.add(labelComando, BorderLayout.NORTH);

        // Painel para os botões e labels
        final var painelBotoes = new JPanel(new GridLayout(4, 3));

        // Botão 1 e label
        final var locLabel = new JLabel("Servidor de Localização:");
        final JLabel locStatusLabel = new JLabel(on(LocalizationService.getAddress()));
        final var locButton = new JButton(TURN_OFF);
        locLabel.setHorizontalAlignment(SwingConstants.CENTER);
        locStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        locButton.addActionListener(e -> {
            if (LOCALIZATION.isAlive()) {
                LOCALIZATION.stop();
                locStatusLabel.setText(OFF);
                locButton.setText(TURN_ON);
            } else {
                LOCALIZATION.run();
                locStatusLabel.setText(ON);
                locButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(locLabel);
        painelBotoes.add(locStatusLabel);
        painelBotoes.add(locButton);

        // Botão 2 e label
        final var proxyLabel = new JLabel("Servidor de Proxy:");
        final var proxyStatusLabel = new JLabel(on(ProxyService.getAddress()));
        ProxyService.addListenerWhenChangeAddress(address -> proxyStatusLabel.setText(on(address)));
        final var proxyButton = new JButton(TURN_OFF);
        proxyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        proxyStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        proxyButton.addActionListener(e -> {
            if (PROXY.isAlive()) {
                PROXY.stop();
                proxyStatusLabel.setText(OFF);
                proxyButton.setText(TURN_ON);
            } else {
                PROXY.run();
                proxyStatusLabel.setText(ON);
                proxyButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(proxyLabel);
        painelBotoes.add(proxyStatusLabel);
        painelBotoes.add(proxyButton);

        // Botão 3 e label
        final var serverLabel = new JLabel("Servidor de Dados:");
        final var serverStatusLabel = new JLabel(ON);
        final var serverButton = new JButton(TURN_OFF);
        serverLabel.setHorizontalAlignment(SwingConstants.CENTER);
        serverStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        serverButton.addActionListener(e -> {
            if (SERVER.isAlive()) {
                SERVER.stop();
                serverStatusLabel.setText(OFF);
                serverButton.setText(TURN_ON);
            } else {
                SERVER.run();
                serverStatusLabel.setText(ON);
                serverButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(serverLabel);
        painelBotoes.add(serverStatusLabel);
        painelBotoes.add(serverButton);

        final var endLabel = new JLabel(
                "<html>Acesse o Servidor de Localização pelo endereço " + Constants.getDefaultHost() + ":"
                        + Constants.LOCALIZATION_PORT + "</html>");
        final var endButton = new JButton("Encerrar tudo");
        endButton.addActionListener(e -> {
            if (LOCALIZATION.isAlive()) {
                LOCALIZATION.stop();
            }
            if (PROXY.isAlive()) {
                PROXY.stop();
            }
            if (SERVER.isAlive()) {
                SERVER.stop();
            }

            janela.dispose();
        });
        painelBotoes.add(endLabel);
        painelBotoes.add(endButton);

        // Adiciona o painel de botões à janela
        janela.add(painelBotoes, BorderLayout.CENTER);

        // Exibe a janela
        janela.setVisible(true);
    }

    private static String on(final InetSocketAddress address) {
        return "<html>Disponível em " + address.getHostString() + ":" + address.getPort() + "</html>";
    }

}