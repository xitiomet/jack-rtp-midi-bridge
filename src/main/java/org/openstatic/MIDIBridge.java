package org.openstatic;

import io.github.leovr.rtipmidi.*;
import io.github.leovr.rtipmidi.session.AppleMidiSession;
import io.github.leovr.rtipmidi.model.MidiMessage;

import java.net.InetAddress;
import java.net.NetworkInterface;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiolibs.jnajack.Jack;
import org.jaudiolibs.jnajack.JackClient;
import org.jaudiolibs.jnajack.JackException;
import org.jaudiolibs.jnajack.JackMidi;
import org.jaudiolibs.jnajack.JackOptions;
import org.jaudiolibs.jnajack.JackPort;
import org.jaudiolibs.jnajack.JackPortFlags;
import org.jaudiolibs.jnajack.JackPortType;
import org.jaudiolibs.jnajack.JackProcessCallback;
import org.jaudiolibs.jnajack.JackShutdownCallback;
import org.jaudiolibs.jnajack.JackStatus;

import org.apache.commons.cli.*;

public class MIDIBridge implements JackProcessCallback, JackShutdownCallback
{
    private final JackClient client;
    private final JackPort inputPort;
    private final JackPort outputPort;
    private final JackMidi.Event midiEvent;
    private AppleMidiServer appleMidiServer;
    private AppleMidiSession session;
    private String hostname = null;
    private ConcurrentLinkedQueue<TimedMidiMessage> jackOutputQueue;
    private JmDNS jmdns;
    private byte[] data;
    private CommandLine commandLineOptions;
    private BlockingQueue<String> debugQueue;
    private boolean keep_running;
    private int bufferSize;

    public static void main(String[] args)
    {
        try
        {
            Options options = new Options();
            CommandLineParser parser = new DefaultParser();

            options.addOption(new Option("d", "debug", false, "Turn on debug."));
            options.addOption(new Option("h", "help", false, "Show command line options and usage."));
            options.addOption(new Option("j", "jackname", true, "Set the interface name for jack."));
            options.addOption(new Option("r", "rtpname", true, "Set the interface name for RTP."));

            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "jamb", options );
                System.exit(0);
            }

            MIDIBridge midiSource = new MIDIBridge(cmd);
            while (midiSource.keep_running)
            {
                Thread.sleep(1000);
            }
        } catch (Exception ex) {

        }
    }

    private MIDIBridge(CommandLine cmdLine) throws JackException
    {
        this.keep_running = true;
        this.commandLineOptions = cmdLine;
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
          public void run()
          {
            MIDIBridge.this.shutDownMDNS();
          }
        });
        InetAddress localHost = this.getLocalHost();
        this.hostname = localHost.getHostName();

        // Setup JACK
        this.jackOutputQueue = new ConcurrentLinkedQueue<TimedMidiMessage>();
        EnumSet<JackStatus> status = EnumSet.noneOf(JackStatus.class);
        try
        {
            String jackName = MIDIBridge.this.commandLineOptions.getOptionValue('j',"RTPMidiBridge");
            Jack jack = Jack.getInstance();
            client = jack.openClient(jackName, EnumSet.of(JackOptions.JackNoStartServer), status);
            if (!status.isEmpty()) {
                System.out.println("JACK client status : " + status);
            }
            this.bufferSize = client.getBufferSize();
            inputPort = client.registerPort("MIDI in", JackPortType.MIDI, JackPortFlags.JackPortIsInput);
            outputPort = client.registerPort("MIDI out", JackPortType.MIDI, JackPortFlags.JackPortIsOutput);
            midiEvent = new JackMidi.Event();
        } catch (JackException ex) {
            if (!status.isEmpty()) {
                System.out.println("JACK exception client status : " + status);
            }
            throw ex;
        }

        // Setup Apple Midi
        try
        {
            String rtpName = MIDIBridge.this.commandLineOptions.getOptionValue('r',"JACKMidiBridge");
            this.jmdns = JmDNS.create(localHost);
            ServiceInfo serviceInfo = ServiceInfo.create("_apple-midi._udp.local.", rtpName, 5004, "JACK MIDI on " + this.hostname);
            jmdns.registerService(serviceInfo);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        this.appleMidiServer = new AppleMidiServer(hostname, 5004);
        this.session = new AppleMidiSession()
        {
          protected void onMidiMessage(final MidiMessage message, final long timestamp)
          {
            try
            {
                // convert frame time to millis instead of micros
                long jackFrameTime = client.getFrameTime();
                long frameTime = Math.floorDiv(jackFrameTime, 10l);
                if (message != null)
                {
                    int ts = (int) (frameTime % MIDIBridge.this.bufferSize);
                    if (MIDIBridge.this.commandLineOptions.hasOption("d"))
                        System.err.println("RTP Recieved Midi Event jft=" + String.valueOf(jackFrameTime) + " tick=" + String.valueOf(ts) + " Data:" + midiDataToString(message.getData()));
                    MIDIBridge.this.jackOutputQueue.add(new TimedMidiMessage(message, ts));
                }
            } catch (JackException je) {
                je.printStackTrace(System.err);
            }
          }
        };
        this.appleMidiServer.addAppleMidiSession(session);
        this.appleMidiServer.start();
        client.setProcessCallback(this);
        client.onShutdown(this);
        client.activate();
    }

    public String midiDataToString(byte[] midiData)
    {
        StringBuilder sb = new StringBuilder();
        sb.setLength(0);
        for (int j = 0; j < midiData.length; j++) {
            sb.append((j == 0) ? "" : ", ");
            sb.append(midiData[j] & 0xFF);
        }
        return sb.toString();
    }



    // THIS LOOP IS IMPORTNANT ALL JACK STUFF MUST BE DONE HERE!
    @Override
    public boolean process(JackClient client, int nframes)
    {
        try
        {
            // Read Events From jack and send to apple midi
            int eventCount = JackMidi.getEventCount(inputPort);
            for (int i = 0; i < eventCount; ++i)
            {
                JackMidi.eventGet(midiEvent, inputPort, i);
                int size = midiEvent.size();
                if (data == null || data.length < size)
                {
                    data = new byte[size];
                }
                midiEvent.read(data);
                long jackFrameTime = client.getLastFrameTime();
                final int tickTs = (int) Math.floorDiv(jackFrameTime, 10l) % this.bufferSize;
                MidiMessage m = new TimedMidiMessage(data, size, tickTs);
                this.session.sendMidiMessage(m, tickTs);
                if (MIDIBridge.this.commandLineOptions.hasOption("d"))
                    System.err.println("JACK Recieved Midi Event jft=" + String.valueOf(jackFrameTime) + " tick=" + String.valueOf(tickTs) + " Data:" + midiDataToString(data));
            }

            // Read Events from AppleMidi Queue and write to jack with frame time
            JackMidi.clearBuffer(outputPort);
            while(jackOutputQueue.peek() != null)
            {
                TimedMidiMessage msg = jackOutputQueue.poll();
                int ts = (int) msg.getTick();
                JackMidi.eventWrite(outputPort, ts, msg.getData(), msg.getLength());
            }
            return true;
        } catch (JackException ex) {
            System.out.println("ERROR : " + ex);
            return false;
        }
    }

    @Override
    public void clientShutdown(JackClient client)
    {
        System.err.println("Jack Client Shutdown");
        this.keep_running = false;
    }

    public void shutDownMDNS()
    {
        if (this.jmdns != null)
        {
            System.err.println("Please Wait for mDNS to unregister....");
            try
            {
                this.jmdns.unregisterAllServices();
                this.jmdns.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    // Figure out the local host ignoring any loopback interfaces.
    private static InetAddress getLocalHost()
    {
        InetAddress ra = null;
        try
        {
            for(Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces(); n.hasMoreElements();)
            {
                NetworkInterface ni = n.nextElement();
                for(Enumeration<InetAddress> e = ni.getInetAddresses(); e.hasMoreElements();)
                {
                    InetAddress ia = e.nextElement();
                    if (!ia.isLoopbackAddress() && ia.isSiteLocalAddress())
                    {
                        System.err.println("Possible Local Address:" + ia.toString());
                        ra = ia;
                    }
                }
            }

        } catch (Exception e) {}
        return ra;
    }
}
