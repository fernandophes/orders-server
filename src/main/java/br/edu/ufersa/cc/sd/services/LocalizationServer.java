package br.edu.ufersa.cc.sd.services;

import java.io.Serializable;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.NotificationDto;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.utils.Constants;

public class LocalizationServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizationServer.class.getSimpleName());

    private static InetSocketAddress proxyAddress = new InetSocketAddress(Constants.getDefaultHost(),
            Constants.PROXY_PORT);

    public LocalizationServer() {
        super(LOG, Nature.APPLICATION, Constants.LOCALIZATION_PORT);
    }

    private static Response<InetSocketAddress> updateSocketAddress(final Serializable item) {
        if (item instanceof NotificationDto) {
            proxyAddress = ((NotificationDto) item).getNewAddress();
            LOG.info("Recebido novo endereço do Proxy: {}", proxyAddress);
            return new Response<>(ResponseStatus.OK);
        } else {
            return new Response<>(ResponseStatus.ERROR,
                    "O objeto precisa ser do tipo InetSocketAddress, tente novamente");
        }
    }

    @Override
    protected Response<? extends Serializable> handleMessage(Request<? extends Serializable> request) {
        if (request.getOperation() == Operation.LOCALIZE) {
            final var item = request.getItem();
            if (item instanceof NotificationDto && ((NotificationDto) item).getNature().equals(Nature.PROXY)) {
                return updateSocketAddress(request.getItem());
            } else {
                return new Response<>(proxyAddress);
            }
        } else {
            return new Response<>(ResponseStatus.ERROR,
                    "O servidor de localização suporta apenas a operação " + Operation.LOCALIZE.toString(),
                    proxyAddress);
        }
    }

}
