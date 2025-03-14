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

import br.edu.ufersa.cc.sd.services.LocalizationServer;
import br.edu.ufersa.cc.sd.services.OrderService;
import br.edu.ufersa.cc.sd.services.ProxyServer;
import br.edu.ufersa.cc.sd.services.ApplicationServer;
import br.edu.ufersa.cc.sd.utils.Constants;

public class Main {

    private static final String OFF = "Desligado";
    private static final String TURN_ON = "Ligar";
    private static final String TURN_OFF = "Desligar";

    private static final Logger LOG = LoggerFactory.getLogger(Main.class.getSimpleName());

    private static final ApplicationServer APPLICATION = new ApplicationServer();
    private static final ProxyServer PROXY = ProxyServer.getInstance();
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
        final JLabel locStatusLabel = new JLabel(on(LocalizationServer.getAddress()));
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
                locStatusLabel.setText(on(LocalizationServer.getAddress()));
                locButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(locLabel);
        painelBotoes.add(locStatusLabel);
        painelBotoes.add(locButton);

        // Botão 2 e label
        final var proxyLabel = new JLabel("Servidor de Proxy:");
        final var proxyStatusLabel = new JLabel(on(ProxyServer.getAddress()));
        ProxyServer.addListenerWhenChangeAddress(address -> proxyStatusLabel.setText(on(address)));
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
                proxyStatusLabel.setText(on(ProxyServer.getAddress()));
                proxyButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(proxyLabel);
        painelBotoes.add(proxyStatusLabel);
        painelBotoes.add(proxyButton);

        // Botão 3 e label
        final var applicationLabel = new JLabel("Servidor de Dados:");
        final var applicationStatusLabel = new JLabel(on(APPLICATION.getAddress()));
        final var applicationButton = new JButton(TURN_OFF);
        applicationLabel.setHorizontalAlignment(SwingConstants.CENTER);
        applicationStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        applicationButton.addActionListener(e -> {
            if (APPLICATION.isAlive()) {
                APPLICATION.stop();
                applicationStatusLabel.setText(OFF);
                applicationButton.setText(TURN_ON);
            } else {
                APPLICATION.run();
                applicationStatusLabel.setText(on(APPLICATION.getAddress()));
                applicationButton.setText(TURN_OFF);
            }
        });
        painelBotoes.add(applicationLabel);
        painelBotoes.add(applicationStatusLabel);
        painelBotoes.add(applicationButton);

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
            if (APPLICATION.isAlive()) {
                APPLICATION.stop();
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