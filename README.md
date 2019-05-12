#JACK MIDI to RTP MIDI Bridge

I was looking for a rather simple way to make JACK stuff talk to Apple MIDI devices (Mainly a mac running ableton) this is the solution i came up with. I Know, I  know, Java for something like this? Why not it works well.

![alt text](https://raw.githubusercontent.com/xitiomet/jack-rtp-midi-bridge/master/res/jack-side.png "Jack Screenshot")

Things you will need to do to prepare your system
- sudo apt-get-install maven openjdk-8-jdk
- ./compile.sh

To run this program just type "./midi-bridge"

```bash
usage: midi-bridge
 -d,--debug            Turn on debug.
 -h,--help             Show command line options and usage.
 -j,--jackname <arg>   Set the interface name for jack.
 -r,--rtpname <arg>    Set the interface name for RTP.
```

Thanks to the creators of:
https://github.com/jaudiolibs/jnajack
https://github.com/LeovR/rtp-midi

for making this project possible
