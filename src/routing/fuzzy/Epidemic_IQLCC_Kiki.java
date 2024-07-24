/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing.fuzzy;

import java.util.*;

import core.*;
import routing.*;
import static routing.MessageRouter.DENIED_OLD;
import static routing.MessageRouter.DENIED_UNSPECIFIED;
import static routing.MessageRouter.RCV_OK;
import static routing.MessageRouter.TRY_LATER_BUSY;
import routing.QL.*;

public abstract class Epidemic_IQLCC_Kiki extends ActiveRouter implements CVDetectionEngine, QVDetectionEngine {

    /**
     * Epidemic_IQLCC router's setting namespace ({@value})
     */
    public static final String Epidemic_IQLCC_NS = "eIQLCC";
    /**
     * AI (additive increase) - setting id (@value)
     */
    public static final String AI_S = "ai";
    /**
     * MD (multiplicative decrease) - setting id (@value)
     */
    public static final String MD_S = "md";
    /**
     * Alpha for CV's learning rate - setting id (@value)
     */
    public static final String ALPHA_CV_S = "alphaCV";
    /**
     * constant K for message generation period (action 4 & 5) - setting id
     * (@value)
     */
    public static final String K_S = "k";
    /**
     * minimum time for update new state (window) - setting id (@value)
     */
    public static final String STATE_UPDATE_INTERVAL_S = "stateInterval";
    /**
     * boltzmann value - setting id (@value)
     */
    public static final String BOLTZMANN_C_S = "boltzmannConsValue";
    /**
     * Congestion Threshold for state update - setting id (@value)
     */
    public static final String CTH_S = "CTH";
    /**
     * Non- Congestion Threshold for state update - setting id (@value)
     */
    public static final String NCTH_S = "NCTH";

    /**
     * default value for ai
     */
    public static final int DEFAULT_AI = 1;
    /**
     * default value for md
     */
    public static final double DEFAULT_MD = 0.2;
    /**
     * default value for alpha
     */
    public static final double DEFAULT_ALPHA_CV = 0.9;
    /**
     * default value for constant K
     */
    public static final double DEFAULT_K = 2.0;
    /**
     * default value for state interval update
     */
    public static final double DEFAULT_STATE_UPDATE_INTERVAL = 300;
    /**
     * default value for boltzmann constant value
     */
    public static final double DEFAULT_BOLTZMANN_C = 0.1;
    /**
     * default value for congestion threshold
     */
    public static final double DEFAULT_CTH = 0.1;
    /**
     * default value for non-congestion threshold
     */
    public static final double DEFAULT_NCTH = 0.00001;

    /**
     * Queue mode for rate, reps, and TTL.
     */
    public static final int Q_MODE_RATE = 0;
    public static final int Q_MODE_REPS = 1;
    public static final int Q_MODE_TTL = 2;

    /**
     * check if the message has the oldest receiving time
     */
    public boolean dropByOldestReceivingTime = true; //nge drop nya dari yang paling lama atau ga

    /**
     * determine which queue mode used for dropping messages based on rate, reps
     * and ttl
     */
    public int deleteQueueMode;

    /**
     * value of cv alpha setting
     */
    private double alpha;
    /**
     * value of stateUpdateInterval setting
     */
    private double stateUpdateInterval;
    /**
     * value of boltzmann setting
     */
    private double boltzmann;
    /**
     * value of CTH setting
     */
    private double CTH;
    /**
     * value of NCTH setting
     */
    private double NCTH;

    /**
     * dummy variable to count number of reps
     */
    private int nrofreps = 0;
    /**
     * dummy variable to count number of drops
     */
    private int nrofdrops = 0;
    /**
     * dummy variable to count reps number of the other hosts
     */
    private int otherNrofReps = 0;
    /**
     * dummy variable to count number of drops number of the other hosts
     */
    private int otherNrofDrops = 0;
    /**
     * to count msg limit for each connection
     */
    private int msglimit = 1;

    /**
     * ratio of drops and reps
     */
    private double ratio = 0;
    /**
     * congestion value - ratio of drops and reps, counted with EWMA equation
     */
    private double CV = 0;

    /**
     * a map to record information about a connection and its limit
     */
    private Map<Connection, Integer> conlimitmap;

    /**
     * dummy variable to set the interval to count the new CV to detect the new
     * state
     */
    private double LastUpdateTimeofState = 0;

    /**
     * needed for CV report
     */
    private List<CVandTime> cvandtime;

    /**
     * buffer that save receipt
     */
    protected Map<String, ACKTTL> receiptBuffer;

    /**
     * message that should be deleted
     */
    protected Set<String> messageReadytoDelete;

    /**
     * QL object init
     */
    private QLearning QL;

    /**
     * action restriction checking table for each state
     */
    protected boolean[][] actionRestriction = {
        {true, true, true, true}, //Non-congested
        {false, false, false, false}, //Partial Congested
        {true, true, true, true},}; //Congested

    /**
     * exploration policy
     */
    protected IExplorationPolicy explorationPolicy;

    //init adalah nilai awal 
    /**
     * init state for congested
     */
    private static final int C = 0;
    /**
     * init state for Non-congested
     */
    private static final int NC = 1;
    /**
     * init state for Partial congested
     */
    private static final int PC = 2;
    /**
     * init state for Prospective congested
     */
//    private static final int PC = 3;

    private static final int RANDOM_DROP = 3; //Action ID untuk Random Drop
    public static final int FORCE_DROP = 4; // Action ID untuk Force Drop

    /**
     * For the first time, the state value is set to -1 to tell the node that
     * this is the first time to do the learning. The node only need to observe
     * the current state and choose an action to receive the first q-value.
     */
    protected int oldstate = -1;

    /**
     * to save the information about the last selected action
     */
    protected int actionChosen;

    /**
     * message generation interval in seconds
     */
    protected double msggenerationinterval = 600;

    /**
     * constant k for increase or decrease message generation period
     */
    private double k = 2;

    /**
     * to record the last time of message creation
     */
    private double endtimeofmsgcreation = 0;

    /**
     * message property to record its number of copies
     */
    public static final String repsproperty = "nrofcopies";

    private MessageRouter router;

    private static final double INITIAL_REWARD = 1.0; // Initial reward value for partial congested state
    private static final double DECAY_RATE = 0.1; // Decay rate for exponential decay
    private double reward; // Current reward value

    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object. //Konstruktor. Membuat pesan baru pada router
     * berdasarkan setting yang diberikan pada setting object.
     *
     * @param s The settings object
     */
    public Epidemic_IQLCC_Kiki(Settings s) {
        super(s);
        Settings Epidemic_IQLCCSettings = new Settings(Epidemic_IQLCC_NS);

        if (Epidemic_IQLCCSettings.contains(ALPHA_CV_S)) {
            alpha = Epidemic_IQLCCSettings.getDouble(ALPHA_CV_S);
        } else {
            alpha = DEFAULT_ALPHA_CV;
        }

        if (Epidemic_IQLCCSettings.contains(STATE_UPDATE_INTERVAL_S)) {
            stateUpdateInterval = Epidemic_IQLCCSettings.getDouble(STATE_UPDATE_INTERVAL_S);
        } else {
            stateUpdateInterval = DEFAULT_STATE_UPDATE_INTERVAL;
        }

        if (Epidemic_IQLCCSettings.contains(K_S)) {
            k = Epidemic_IQLCCSettings.getDouble(K_S);
        } else {
            k = DEFAULT_K;
        }

        if (Epidemic_IQLCCSettings.contains(BOLTZMANN_C_S)) {
            boltzmann = Epidemic_IQLCCSettings.getDouble(BOLTZMANN_C_S);
        } else {
            boltzmann = DEFAULT_BOLTZMANN_C;
        }

        if (Epidemic_IQLCCSettings.contains(CTH_S)) {
            CTH = Epidemic_IQLCCSettings.getDouble(CTH_S);
        } else {
            CTH = DEFAULT_CTH;
        }

        if (Epidemic_IQLCCSettings.contains(NCTH_S)) {
            NCTH = Epidemic_IQLCCSettings.getDouble(NCTH_S);
        } else {
            NCTH = DEFAULT_NCTH;
        }

        explorationPolicy();
        initQL();
        limitconmap();
        cvtimelist();
        receiptbuffer();
        msgreadytodelete();
    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected Epidemic_IQLCC_Kiki(Epidemic_IQLCC_Kiki r) {
        super(r);
        this.alpha = r.alpha;
        this.k = r.k;
        this.stateUpdateInterval = r.stateUpdateInterval;
        this.boltzmann = r.boltzmann;
        this.CTH = r.CTH;
        this.NCTH = r.NCTH;
        explorationPolicy();
        initQL();
        limitconmap();
        cvtimelist();
        receiptbuffer();
        msgreadytodelete();
    }

    /**
     * Initializes exploration policy
     */
    protected void explorationPolicy() {
        this.explorationPolicy = new BoltzmannExploration(1);
    }

    protected void initQL() {

        this.QL = new QLearning(this.actionRestriction.length, this.actionRestriction[0].length, this.explorationPolicy,
                false, this.actionRestriction);

    }

    protected void limitconmap() { //Method yang digunakan untuk informasi tentang koneksi dan limitnya. 
        this.conlimitmap = new HashMap<Connection, Integer>(); //Menggunakan hashmap yang menyimpan Connection dan Integer (untuk limit)
    }

    protected void cvtimelist() { //Method yang digunakan untuk informasi laporan CV 
        this.cvandtime = new ArrayList<CVandTime>(); //CV and Time menyimpan data CV dan waktu ketika update CV tersebut
    }

    protected void receiptbuffer() { //Method yang digunakan untuk menyimpan nilai buffer yang menyimpan resi(receipt). 
        this.receiptBuffer = new HashMap<>(); //Membuat sebuah objek HashMap baru yang akan disimpan dalam variabel receiptBuffer.
    }

    protected void msgreadytodelete() { //Method yang digunakan untuk menyimpan data message yang harus dihapus
        this.messageReadytoDelete = new HashSet<>(); // Objek HashSet ini akan digunakan untuk menyimpan elemen-elemen unik tanpa duplikat pada variabel messageReadytoDelete
    }

    @Override
    public void changedConnection(Connection con) { //Method yang digunakan untuk menyimpan perubahan koneksi yang menyimpan parameter Connection
        if (con.isUp()) { //Jika Connection up atau koneksi sedang aktif

            connectionUp(con);

            /*peer's router */ //Router tetangga atau teman
            DTNHost otherHost = con.getOtherNode(getHost()); //mencari host lain pada koneksi con untuk mendapatkan host yang terhubung oleh node tujuan

            conlimitmap.put(con, this.msglimit); //

            Collection<Message> thisMsgCollection = getMessageCollection(); //

            Epidemic_IQLCC_Kiki peerRouter = (Epidemic_IQLCC_Kiki) otherHost.getRouter(); //
            exchangemsginformation();
            Map<String, ACKTTL> peerRB = peerRouter.getReceiptBuffer();
            for (Map.Entry<String, ACKTTL> entry : peerRB.entrySet()) {
                if (!receiptBuffer.containsKey(entry.getKey())) {
                    receiptBuffer.put(entry.getKey(), entry.getValue());

                }

            }
            for (Message m : thisMsgCollection) {
                /**
                 * Delete message that have a receipt
                 */
                if (receiptBuffer.containsKey(m.getId())) {
                    messageReadytoDelete.add(m.getId());
                }
            }
            // delete transferred msg
            for (String m : messageReadytoDelete) {

                deletemsg(m, false);
            }

            messageReadytoDelete.clear();
        } else {
            connectionDown(con);
            DTNHost otherHost = con.getOtherNode(getHost());
            Epidemic_IQLCC_Kiki peerRouter = (Epidemic_IQLCC_Kiki) otherHost.getRouter();
            /* record the peer's nrofdrops & nrofreps 
			 * as otherNrofDrops & otherNrofReps  */
            otherNrofDrops += peerRouter.getNrofDrops();
            otherNrofReps += peerRouter.getNrofReps();
            conlimitmap.remove(con);
            messageReadytoDelete.clear();
        }
    }

    /**
     * before deleting the message, check if the message is being sent
     */
    public void deletemsg(String msgID, boolean dropchecking) {
        if (isSending(msgID)) {
            List<Connection> conList = getConnections();
            for (Connection cons : conList) {
                if (cons.getMessage() != null && cons.getMessage().getId() == msgID) {
                    cons.abortTransfer();
                    break;
                }
            }
        }
        deleteMessage(msgID, dropchecking);
    }

    /**
     * update the CV and choose the action
     */
    @Override
    public void update() {
        super.update();
        if ((SimClock.getTime() - LastUpdateTimeofState) >= stateUpdateInterval) {

            double newCV = countcv();
            CVandTime nilaicv = new CVandTime(newCV, SimClock.getTime());
            cvandtime.add(nilaicv);
            if (this.oldstate == -1) {
                oldstate = staterequirement(this.CV, newCV);
                actionChosen = this.QL.GetAction(oldstate);
                this.actionSelectionController(actionChosen);
            } else {
                int newstate = staterequirement(this.CV, newCV);
                this.updateState(newstate);
            }
            this.CV = newCV;
            LastUpdateTimeofState = SimClock.getTime();

        }
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        /**
         * use it in the routing class
         */
        // tryAllMessageToAllConections();
    }

    /* exchange mesage's information of the reps number **/
    protected void exchangemsginformation() { //
        Collection<Message> msgCollection = getMessageCollection();
        for (Connection con : getConnections()) {
            DTNHost peer = con.getOtherNode(getHost());
            Epidemic_IQLCC_Kiki other = (Epidemic_IQLCC_Kiki) peer.getRouter();
            if (other.isTransferring()) {
                continue; // skip hosts that are transferring
            }
            for (Message m : msgCollection) {
                if (other.hasMessage(m.getId())) {
                    Message temp = other.getMessage(m.getId());
                    /* take the max reps */
                    if ((Integer) m.getProperty(repsproperty) < (Integer) temp.getProperty(repsproperty)) {
                        m.updateProperty(repsproperty, temp.getProperty(repsproperty));
                    }
                }

            }
        }
    }

    /* the procedure of updating the state*/
    protected void updateState(int newstate) {

        double reward = checkReward(oldstate, newstate);

        this.QL.UpdateState(oldstate, actionChosen, reward, newstate);

        int newestAction = this.QL.GetAction(newstate);

        this.actionSelectionController(newestAction);
        this.oldstate = newstate;
        this.actionChosen = newestAction;
        System.out.println(newestAction);
        BoltzmannExploration exp = (BoltzmannExploration) this.QL.getExplorationPolicy();
        double temp = exp.getTemperature();
        if (temp != 0) {
            /**
             * do exploration 100 times
             */
            exp.setTemperature(temp - boltzmann);
        }
        if (temp <= 0) {
            exp.setTemperature(0);
        }
        this.QL.setExplorationPolicy(exp);
    }

    /**
     * action selection controller
     */
    public void actionSelectionController(int action) {
        switch (action) {
            case FORCE_DROP:
                this.dropbasedonoldestTTL();
                break;

            case RANDOM_DROP:
                Random randomGenerator = new Random();
                int randomNumber = randomGenerator.nextInt(4); // 0-3 inclusive
                switch (randomNumber) {
                    case 0:
                        this.dropbasedonhighestrate();
                        break;
                    case 1:
                        this.dropbasedonhighestnrofreps();
                        break;
                    case 2:
                        this.dropbasedonoldestTTL();
                        break;
                    case 3:
                        this.dropbasedonoldestReceivingTime();
                        break;
                }
                break;
        }
    }

    /**
     * state transition's requirement
     */
    // State, non-congested, congested, partial congested 
    // Congested: 
    // Partial congested: 
    // Non congested: 
    protected int staterequirement(double oldcv, double newcv) {
        if (newcv >= CTH) {
            return C; //0.1 (haggle), 0.5 (rwp)
        } else if (newcv <= NCTH) {
            return NC; //0.00001 (haggle). 0.1(rwp)
        } else if (newcv <= oldcv) {
            return PC;
        }
        return 0;

    }

    @Override
    protected int startTransfer(Message m, Connection con) {
        int retVal;

        if (!con.isReadyForTransfer()) {
            return TRY_LATER_BUSY;
        }
        /* start transferring if the connection still has the remaining msg limit*/
        if (conlimitmap.containsKey(con)) {
            retVal = con.startTransfer(getHost(), m);
            if (retVal == RCV_OK) { // started transfer
                addToSendingConnections(con);
                /* set the limit left from a connection as a remaining limit*/
                int remaininglimit = conlimitmap.get(con);
                /* if the message can be transferred, limit decreased by 1*/
                remaininglimit = remaininglimit - 1;
                /* if there's still any limit left, set the remaining limit as the new one.
				 * if there's no any limit left, remove the connection to prevent the node 
				 * from a sending a message to the connection.*/
                if (remaininglimit != 0) {
                    conlimitmap.replace(con, remaininglimit);
                } else {
                    conlimitmap.remove(con);
                }
            } else if (deleteDelivered && retVal == DENIED_OLD && m.getTo() == con.getOtherNode(this.getHost())) {
                /* final recipient has already received the msg -> delete it */
                this.deleteMessage(m.getId(), false);
            }
            return retVal;
        }

        return DENIED_UNSPECIFIED;

    }

    /**
     * buffer checking
     */
    @Override
    protected boolean makeRoomForMessage(int size) {
        if (size > this.getBufferSize()) {
            return false; // message too big for the buffer
        }

        int freeBuffer = this.getFreeBufferSize();
        /* delete messages from the buffer until there's enough space */

 /* if dropByOldestReceivingTime is true, message are deleted by the oldest
		 * receiving time, if it's false, message are deleted based on the delete queue mode*/
        if (dropByOldestReceivingTime) {
            while (freeBuffer < size) {
                Message m = getOldestMessage(true); // don't remove msgs being sent

                if (m == null) {
                    return false; // couldn't remove any more messages
                }

                /* delete message from the buffer as "drop" */
                deleteMessage(m.getId(), true);
                nrofdrops++;
                freeBuffer += m.getSize();
            }

            return true;
        }
        List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
        deleteSortByQueueMode(messages);

        for (Message m : messages) {
            if (freeBuffer < size) {
                if (freeBuffer + m.getSize() > this.getBufferSize()) {
                    // Drop by TTL
//                    if (m.getTtl() <= 0) {
                    deleteMessage(m.getId(), true);
                    nrofdrops++;
                    freeBuffer += m.getSize();
//                    }
                } else if (freeBuffer + m.getSize() > this.getBufferSize()) {
                    // Random drop
                    if (Math.random() < 0.33) {
                        deleteMessage(m.getId(), true);
                        nrofdrops++;
                        freeBuffer += m.getSize();
                    }
                } else {
                    deleteMessage(m.getId(), true);
                    nrofdrops++;
                    freeBuffer += m.getSize();
                }
            } else {
                return true;
            }
        }

        if (freeBuffer < size) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * to sort delete queue
     */
    @SuppressWarnings(value = "unchecked")
    /* ugly way to make this generic */
    protected List deleteSortByQueueMode(List list) {
        switch (deleteQueueMode) {
            /**
             * Compares messages by the highest rate
             */
            case Q_MODE_RATE:
                Collections.sort(list, new Comparator() {

                    public int compare(Object o1, Object o2) {
                        double diff;
                        Message m1, m2;

                        if (o1 instanceof Tuple) {
                            m1 = ((Tuple<Message, Connection>) o1).getKey();
                            m2 = ((Tuple<Message, Connection>) o2).getKey();
                        } else if (o1 instanceof Message) {
                            m1 = (Message) o1;
                            m2 = (Message) o2;
                        } else {
                            throw new SimError("Invalid type of objects in " + "the list");
                        }
                        double r1 = ((double) m1.getHops().size() - 1.0)
                                / ((double) m1.getInitTTL() - (double) m1.getTtl());
                        double r2 = ((double) m2.getHops().size() - 1.0)
                                / ((double) m2.getInitTTL() - (double) m2.getTtl());

                        /* descending sort */
                        if (r2 - r1 == 0) {
                            /* equal probabilities -> let queue mode decide */
                            return 0;
                        } else if (r2 - r1 < 0) {
                            return -1;
                        } else {
                            return 1;
                        }

                    }
                });
                break;
            case Q_MODE_REPS:
                Collections.sort(list, new Comparator() {
                    /**
                     * Compares messages by the highest number of replications
                     */
                    public int compare(Object o1, Object o2) {
                        double diff;
                        Message m1, m2;

                        if (o1 instanceof Tuple) {
                            m1 = ((Tuple<Message, Connection>) o1).getKey();
                            m2 = ((Tuple<Message, Connection>) o2).getKey();
                        } else if (o1 instanceof Message) {
                            m1 = (Message) o1;
                            m2 = (Message) o2;
                        } else {
                            throw new SimError("Invalid type of objects in " + "the list");
                        }
                        double reps1 = (Integer) m1.getProperty(repsproperty);
                        double reps2 = (Integer) m2.getProperty(repsproperty);

                        /* descending sort */
                        if (reps2 - reps1 == 0) {
                            /* equal probabilities -> let queue mode decide */
                            return 0;
                        } else if (reps2 - reps1 < 0) {
                            return -1;
                        } else {
                            return 1;
                        }

                    }
                });
                break;
            case Q_MODE_TTL:
                Collections.sort(list, new Comparator() {
                    /**
                     * Compares messages by the oldest TTL
                     */
                    public int compare(Object o1, Object o2) {
                        double diff;
                        Message m1, m2;

                        if (o1 instanceof Tuple) {
                            m1 = ((Tuple<Message, Connection>) o1).getKey();
                            m2 = ((Tuple<Message, Connection>) o2).getKey();
                        } else if (o1 instanceof Message) {
                            m1 = (Message) o1;
                            m2 = (Message) o2;
                        } else {
                            throw new SimError("Invalid type of objects in " + "the list");
                        }
                        double ttl1 = m1.getTtl();
                        double ttl2 = m2.getTtl();

                        /* ascending sort */
                        if (ttl2 - ttl1 == 0) {
                            /* equal probabilities -> let queue mode decide */
                            return 0;
                        } else if (ttl2 - ttl1 < 0) {
                            return 1;
                        } else {
                            return -1;
                        }

                    }
                });
                break;
            /* add more queue modes here */
            default:
                throw new SimError("Unknown queue mode " + deleteQueueMode);
        }

        return list;
    }

    @Override

    public boolean createNewMessage(Message m) {
        if (this.endtimeofmsgcreation == 0
                || SimClock.getTime() - this.endtimeofmsgcreation >= this.msggenerationinterval) {
            this.endtimeofmsgcreation = SimClock.getTime();
            /* added repsproperty to count the 
			 * number of replications for a new message*/
            m.addProperty(repsproperty, 1);
            return super.createNewMessage(m);
        }

        return false;

    }

    //ketika sudah menerima pesan, dia ngapain. 
    //
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message aCopy = super.messageTransferred(id, from);
        Integer msgprop = ((Integer) aCopy.getProperty(repsproperty)) + 1;

        aCopy.updateProperty(repsproperty, msgprop);

        // replications are counted by successful incoming replications.
        // +1 for 1 rep./
        nrofreps++;
        // ack
        if (isFinalDest(aCopy, this.getHost()) && !receiptBuffer.containsKey(aCopy.getId())) {
            ACKTTL ack = new ACKTTL(SimClock.getTime(), aCopy.getTtl());
            receiptBuffer.put(aCopy.getId(), ack);
        }

        return aCopy;
    }

    /**
     * check if this host is the final dest
     */
    protected boolean isFinalDest(Message m, DTNHost thisHost) {
        return m.getTo().equals(thisHost);
    }

    /**
     * count message hops
     */
    protected int msgtotalhops() {
        Collection<Message> msg = getMessageCollection();
        int totalhops = 0;
        if (!msg.isEmpty()) {
            for (Message m : msg) {
                if (m.getHopCount() != 0) {
                    totalhops += (m.getHopCount() - 1);
                }
            }
        }
        return totalhops;
    }

    /**
     * calculate the CV
     */
    protected double countcv() {
        int totalhops = msgtotalhops();
        int totaldrop = this.nrofdrops + this.otherNrofDrops;
        int totalreps = this.nrofreps + totalhops + this.otherNrofReps;

        // reset 
        nrofdrops = 0;
        nrofreps = 0;
        otherNrofDrops = 0;
        otherNrofReps = 0;

        double ratio;
        if (totalreps != 0) {
            ratio = (double) totaldrop / (double) totalreps;
            this.ratio = ratio;
            return (alpha * ratio) + ((1.0 - alpha) * CV);
        } else {
            return CV;
        }

    }

    /**
     * IQL reward
     */
    protected double checkReward(int olds, int news) {
        if (olds == NC && news == NC) {
            return 10.0; // Reward for non congested state
        } else if (C == C) {
            return -10.0; // Reward for congested state
        } else {
            // Calculate reward for partial congested state using exponential decay
            double partialCongestedReward = reward;
            reward *= Math.exp(-DECAY_RATE);
            return partialCongestedReward;
        }
    }

    /**
     * IQL ACTION METHODS
     */
    private void dropbasedonhighestrate() {
        dropByOldestReceivingTime = false;
        deleteQueueMode = Q_MODE_RATE;
    }

    private void dropbasedonhighestnrofreps() {
        dropByOldestReceivingTime = false;
        deleteQueueMode = Q_MODE_REPS;
    }

    private void dropbasedonoldestTTL() {
        dropByOldestReceivingTime = false;
        deleteQueueMode = Q_MODE_TTL;
    }

    private void dropbasedonoldestReceivingTime() {
        dropByOldestReceivingTime = true;
    }

    /**
     * when connection up
     */
    public void connectionUp(Connection con) {

    }

    /**
     * when connection down
     */
    public void connectionDown(Connection con) {

    }

    public Map<String, ACKTTL> getReceiptBuffer() {
        return receiptBuffer;
    }

    public int getNrofReps() {
        return this.nrofreps;
    }

    public int getNrofDrops() {
        return this.nrofdrops;
    }

    @Override
    /* needed for CV report */
    public List<CVandTime> getCVandTime() {
        return this.cvandtime;
    }

    @Override
    public double[][] getQV() {
        /* to record the q-values */
        return this.QL.getqvalues();
    }

}

//FUNGSI HASHMAP : Digunakan untuk menyimpan data key-value. Dimana key digunakan untuk mengakses value tertentu.
