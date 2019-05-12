package org.openstatic;

import io.github.leovr.rtipmidi.model.MidiMessage;

public class TimedMidiMessage extends MidiMessage
{
    private int tick;

    public TimedMidiMessage(MidiMessage m, int tick)
    {
        this(m.getData(), tick);
    }

    public TimedMidiMessage(final byte[] bytes, int tick)
    {
        this(bytes, bytes.length, tick);
    }

    public TimedMidiMessage(final byte[] bytes, int size, int tick)
    {
        super(bytes, size);
        this.tick = tick;
    }

    public int getTick()
    {
        return this.tick;
    }
}
