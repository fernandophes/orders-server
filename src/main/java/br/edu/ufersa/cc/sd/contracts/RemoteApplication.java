package br.edu.ufersa.cc.sd.contracts;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

import br.edu.ufersa.cc.sd.dto.Combo;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;

public interface RemoteApplication extends Remote {

    public void addProxy(final Combo proxy) throws RemoteException;

    public void removeProxy(final Combo proxy) throws RemoteException;

    public void addBackup(final Combo backup) throws RemoteException;

    public void removeBackup(final Combo backup) throws RemoteException;

    public <T extends Serializable> Response<T> handleMessage(final Request<? extends Serializable> request) throws RemoteException;

}
