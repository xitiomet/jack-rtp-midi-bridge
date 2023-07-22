# JACK MIDI to RTP MIDI Bridge

I was looking for a rather simple way to make JACK stuff talk to Apple MIDI devices (Mainly a mac running ableton) this is the solution i came up with. I Know, I  know, Java for something like this? Why not it works well. I'm not positive i've mastered all the timing aspects, however i've pumped midi files in both directions and it sounded good. If you have any suggestions please feel free to ask away.

**On the linux/Jack Side**

![alt text](https://raw.githubusercontent.com/xitiomet/jack-rtp-midi-bridge/master/res/jack-side.png "Jack Screenshot")

**On The Mac/Apple/RTP Side**

![alt text](https://raw.githubusercontent.com/xitiomet/jack-rtp-midi-bridge/master/res/mac-side.png "Jack Screenshot")

Things you will need to do to prepare your system
- ``sudo apt-get install maven openjdk-8-jdk libjack-jackd2-dev``
- ``./compile.sh``

To run this program just type "jamb" (it must be run from the machine running jack)

```bash
usage: jamb
 -d,--debug            Turn on debug.
 -h,--help             Show command line options and usage.
 -j,--jackname <arg>   Set the interface name for jack.
 -r,--rtpname <arg>    Set the interface name for RTP.
```

Thanks to the creators of:

- https://github.com/jaudiolibs/jnajack
- https://github.com/LeovR/rtp-midi

for making this project possible
