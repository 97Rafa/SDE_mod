package infore.SDE.synopses;

import infore.SDE.messages.Estimation;
import infore.SDE.messages.Request;

public abstract class ContinuousSynopsis extends Synopsis{

    Request rq;

    public ContinuousSynopsis(int ID, String k, String v) {
        super(ID, k, v);
    }

    public abstract Estimation addEstimate(Object k);

    public Request getRq() {
        return rq;
    }

    public void setRq(Request rq) {
        this.rq = rq;
    }


}
