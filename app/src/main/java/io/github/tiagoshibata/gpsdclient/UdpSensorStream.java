package io.github.tiagoshibata.gpsdclient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;

class UdpSensorStream {
    private final String TAG = "UdpSensorStream";

    private class NetworkThread extends Thread {
        private ArrayBlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(30);
        private boolean running = true;
        private InetAddress address;
        private int port;
        private MulticastSocket multicastSocket;

        private NetworkThread(SocketAddress socketAddress) throws IOException {
            // Extract address and port from SocketAddress
            if (!(socketAddress instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("SocketAddress must be an InetSocketAddress");
            }

            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            this.address = inetSocketAddress.getAddress();
            this.port = inetSocketAddress.getPort();

            multicastSocket = new MulticastSocket();

            // Set multicast options
            multicastSocket.setTimeToLive(255);  // Maximum TTL for hotspot scenarios
            multicastSocket.setLoopbackMode(false);  // Enable loopback (false = enabled)

            // Try to bind to the WiFi hotspot interface
            try {
                NetworkInterface netIf = getWifiHotspotInterface();
                if (netIf != null) {
                    multicastSocket.setNetworkInterface(netIf);
                    Log.i(TAG, "Bound to interface: " + netIf.getName());
                } else {
                    Log.w(TAG, "Could not find WiFi hotspot interface, using default");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not set network interface: " + e.getMessage());
            }

            Log.i(TAG, "Multicast configured for " + address + ":" + port);
        }

        private NetworkInterface getWifiHotspotInterface() {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface netIf = interfaces.nextElement();
                    if (netIf.isUp() && !netIf.isLoopback()) {
                        // Look for typical hotspot interface names
                        String name = netIf.getName().toLowerCase();
                        if (name.startsWith("ap") || name.startsWith("wlan") ||
                                name.startsWith("swlan") || name.contains("hotspot")) {
                            Log.i(TAG, "Found potential hotspot interface: " + name);
                            return netIf;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting network interfaces: " + e.getMessage());
            }
            return null;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    byte[] message = messageQueue.take().getBytes();

                    // Send to multicast address
                    multicastSocket.send(
                            new DatagramPacket(message, message.length, address, port)
                    );

                } catch (InterruptedException e) {
                    // Ignored (will check "running" variable at end of loop)
                } catch (IOException e) {
                    Log.w(TAG, "Multicast send error: " + e.toString());
                }
            }
            multicastSocket.close();
        }

        private void stopThread() {
            running = false;
            interrupt();
        }
    }

    private NetworkThread networkThread;

    UdpSensorStream(SocketAddress address) throws IOException {
        networkThread = new NetworkThread(address);
        networkThread.start();
    }

    /**
     * Queue data to the networking thread.
     * <p>
     * The offer method is used, which is non-blocking, to avoid lockups if
     * called from the UI thread. Note, however, that the message might be
     * discarded if the queue is full (unlikely/impossible in our scenario,
     * since UDP is used for transport, and GPS messages have low frequency).
     *
     * @param  data data to be transmitted
     */
    void send(final String data) {
        if (!networkThread.messageQueue.offer(data))
            Log.w(TAG, "Failed to send: network queue full");
    }

    void stop() {
        networkThread.stopThread();
    }
}