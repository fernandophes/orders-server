package br.edu.ufersa.cc.sd.contracts;

import java.rmi.Remote;
import java.rmi.RemoteException;

import br.edu.ufersa.cc.sd.models.Order;

public interface RemoteProxy extends Remote {

    public Order get(final Long code) throws RemoteException;

}
