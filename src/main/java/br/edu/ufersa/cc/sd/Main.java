package br.edu.ufersa.cc.sd;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.repositories.OrderRepository;
import br.edu.ufersa.cc.sd.services.LocalizationService;
import br.edu.ufersa.cc.sd.services.ProxyService;
import br.edu.ufersa.cc.sd.services.ServerService;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class.getSimpleName());

    public static void main(String[] args) throws SQLException {
        LOG.info("Inicializando banco de dados...");
        OrderRepository.initialize();

        LOG.info("Inicializando servidor...");
        final var server = new ServerService();
        server.run();

        LOG.info("Inicializando servidor de proxy...");
        final var proxy = new ProxyService();
        proxy.run();

        LOG.info("Inicializando servidor de localização...");
        final var localization = new LocalizationService();
        localization.run();

    }

}