/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.DTNHost;
import core.SimClock;
import core.SimScenario;
import core.UpdateListener;
import java.util.*;
import routing.Epidemic_IQLCCExponentialReward;

/**
 *
 * @author ASUS
 */
public class KikiReport extends Report implements UpdateListener {

    private Map<DTNHost, List<Double>> awikwok;
    private double lastUpdate = 0.0;
    private double interval = 1800;

    public KikiReport() {
        init();
        awikwok = new HashMap<>();
    }

    public void updated(List<DTNHost> hosts) {
        for (DTNHost host : hosts) {
            Epidemic_IQLCCExponentialReward rtr = (Epidemic_IQLCCExponentialReward) host.getRouter();
            if (SimClock.getTime() - lastUpdate >= interval) {
                List<Double> val = (awikwok.get(host) != null) ? awikwok.get(host) : new ArrayList<>();
                val.add(rtr.rewardReport);
                awikwok.put(host, val);
                
                double currentBuffer = host.getBufferOccupancy();
                if(host.toString().equals("p0")) {
                    if(currentBuffer > 33.0 && currentBuffer < 66.0) {
                        printLn(host.getBufferOccupancy(), rtr.rewardReport);
                    }
                }
                
                lastUpdate = SimClock.getTime();
            }
        }
    }
    
    private void printLn(double buffer, double reward) {
        String tes = buffer + "\t: " + reward;
        
        write(tes);
    }

    public void done() {
//        String tes = "";
//        for (Map.Entry<DTNHost, List<Double>> entry : awikwok.entrySet()) {
//            if (entry.getKey().toString().equals("p0")) {
////                tes += entry.getKey() + " : " + entry.getValue();
//                for (double data : entry.getValue()) {
//                    tes += data + "\n";
//                }
//            }
//        }

//        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
//        for (DTNHost h : hosts) {
//            tes += h + "\n";
//        }
//        write(tes);
        super.done();
    }
}
