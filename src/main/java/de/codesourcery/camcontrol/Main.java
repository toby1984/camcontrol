package de.codesourcery.camcontrol;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JFrame
{
    private static final String CONTROLS_TEXT = """
                     brightness 0x00980900 (int)    : min=0 max=255 step=1 default=128 value=128
                       contrast 0x00980901 (int)    : min=0 max=255 step=1 default=128 value=128
                     saturation 0x00980902 (int)    : min=0 max=255 step=1 default=128 value=128
 white_balance_temperature_auto 0x0098090c (bool)   : default=1 value=1
                           gain 0x00980913 (int)    : min=0 max=255 step=1 default=0 value=219
           power_line_frequency 0x00980918 (menu)   : min=0 max=2 default=2 value=2
      white_balance_temperature 0x0098091a (int)    : min=2000 max=6500 step=1 default=4000 value=2892 flags=inactive
                      sharpness 0x0098091b (int)    : min=0 max=255 step=1 default=128 value=128
         backlight_compensation 0x0098091c (int)    : min=0 max=1 step=1 default=0 value=0
                  exposure_auto 0x009a0901 (menu)   : min=0 max=3 default=3 value=3
              exposure_absolute 0x009a0902 (int)    : min=3 max=2047 step=1 default=250 value=583 flags=inactive
         exposure_auto_priority 0x009a0903 (bool)   : default=0 value=1
                   pan_absolute 0x009a0908 (int)    : min=-36000 max=36000 step=3600 default=0 value=0
                  tilt_absolute 0x009a0909 (int)    : min=-36000 max=36000 step=3600 default=0 value=0
                 focus_absolute 0x009a090a (int)    : min=0 max=250 step=5 default=0 value=40 flags=inactive
                     focus_auto 0x009a090c (bool)   : default=1 value=1
                  zoom_absolute 0x009a090d (int)    : min=100 max=500 step=1 default=100 value=100
                      led1_mode 0x0a046d05 (menu)   : min=0 max=3 default=0 value=3
                 led1_frequency 0x0a046d06 (int)    : min=0 max=255 step=1 default=0 value=0        
        """;

    private static final Pattern PATTERN =
        Pattern.compile("^\s*([_0-9a-z]+)\s*([x0-9a-f]+)\s*\\((.*?)\\)\s*:(.*)$");

    public enum Flag {
        INACTIVE
    }

    private static class Control<T>
    {
        private final String name;
        public Set<Flag> flags = new HashSet<>();
        private T value;

        private Control(String name)
        {
            this.name = name;
        }

        public boolean isInactive() {
            return flags.contains(Flag.INACTIVE);
        }

        public void setFlags(Set<Flag> flags)
        {
            this.flags = flags;
        }

        public String getName()
        {
            return name;
        }

        public T getValue()
        {
            return value;
        }

        public void setValue(T value)
        {
            this.value = value;
        }
    }

    public static final class IntControl extends Control<Integer> {

        private int min;
        private int max;
        private int step;
        private int defaultValue;

        public IntControl(String name)
        {
            super(name);
        }

        public int getMin()
        {
            return min;
        }

        public void setMin(int min)
        {
            this.min = min;
        }

        public int getMax()
        {
            return max;
        }

        public void setMax(int max)
        {
            this.max = max;
        }

        public int getStep()
        {
            return step;
        }

        public void setStep(int step)
        {
            this.step = step;
        }

        public int getDefaultValue()
        {
            return defaultValue;
        }

        public void setDefaultValue(int defaultValue)
        {
            this.defaultValue = defaultValue;
        }
    }

    public static final class BooleanControl extends Control<Boolean> {

        public BooleanControl(String name)
        {
            super(name);
        }
    }


    public Main() throws HeadlessException
    {
        super("CamControl");

        final Map<String, Control<?>> result = parse();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout( new GridLayout(result.size(), 3 ) );

        for (Map.Entry<String, Control<?>> entry : result.entrySet() ) {

            final Control<?> control = entry.getValue();
            add( new JLabel( control.getName() ), 0 );
            final JCheckBox isActive = new JCheckBox("Active");
            isActive.setSelected( ! control.isInactive() );
            add(isActive, 1 );

            if ( control instanceof BooleanControl bctrl ) {
                final JCheckBox selected = new JCheckBox("Value");
                selected.setSelected( bctrl.getValue() );
                registerListener(selected,bctrl );
                add(selected, 2 );
            } else if ( control instanceof IntControl ictrl ) {

                final JSlider slider = new JSlider( ictrl.getMin(), ictrl.getMax() );
                if ( ictrl.step != 1 )
                {
                    slider.setMajorTickSpacing(ictrl.step);
                }
                slider.setValue( ictrl.getValue() );
                registerListener(slider,ictrl );
                add(slider, 2 );
            } else {
                throw new RuntimeException("Unreachable code reached");
            }
        }

        setPreferredSize(new Dimension(480,320));
        setLocationRelativeTo(null);
        pack();
        setVisible(true);
    }

    private static void registerListener(JSlider slider, IntControl ctrl) {
        final ChangeListener listener = ev -> {
            ctrl.setValue( slider.getValue() );
            setValue( ctrl );
        };
        slider.addChangeListener(listener);
    }

    private static void registerListener(JCheckBox cb, BooleanControl ctrl) {
        cb.addActionListener(ev -> {
            ctrl.setValue(cb.isSelected());
            setValue( ctrl );
        });
    }

    private static void setValue(IntControl ctrl)
    {
        setValue( ctrl.getName(), ctrl.getValue() );
    }

    private static void setValue(BooleanControl ctrl)
    {
        setValue( ctrl.getName(), ctrl.getValue() ? 1 : 0);
    }

    private static void setValue(String key, int value)
    {
        try
        {
            System.out.println("Setting " + key + "=" + value);
             Runtime.getRuntime().exec("v4l2-ctl --set-ctrl="+key+"="+value);
        }
        catch (Exception e)
        {
            System.err.println("Failed to set '" + key + "' to " + value);
            e.printStackTrace();
        }
    }

    private Map<String,Control<?>> parse() {

        final Map<String,Control<?>> result = new TreeMap<>();

        for ( String line : CONTROLS_TEXT.split("\n") ) {
            final Matcher matcher = PATTERN.matcher(line);
            if ( ! matcher.matches() ) {
                throw new IllegalStateException("Unmatched line: >"+line+"<");
            }

            final String name = matcher.group(1);
            final String type = matcher.group(3);
            final String params = matcher.group(4);

            final Map<String,String> paramMap = new HashMap<>();
            for ( String keyValue : params.trim().split("\s") ) {
                final String[] parts = keyValue.split("=");
                if ( paramMap.put(parts[0],parts[1]) != null ) {
                    throw new RuntimeException("Duplicate key '" + parts[0] + "'");
                }
            }

            final Control<?> cntrl = switch( type ) {
                case "int", "menu"-> {
                    final IntControl intControl = new IntControl(name);
                    extract(paramMap,"min",Integer::parseInt, intControl::setMin );
                    extract(paramMap,"max",Integer::parseInt, intControl::setMax );
                    extract(paramMap,"value",Integer::parseInt, intControl::setValue );
                    extract(paramMap,"default",Integer::parseInt, intControl::setDefaultValue );
                    extract(paramMap,"step",Integer::parseInt, intControl::setStep );
                    extract(paramMap,"flags", x -> "inactive".equals(x) ? Set.of(Flag.INACTIVE) : Set.of(), intControl::setFlags);
                    yield intControl;
                }
                case "bool" -> {
                    final BooleanControl boolCtl = new BooleanControl(name);
                    extract(paramMap,"value", "1"::equals, boolCtl::setValue );
                    extract(paramMap,"flags", x -> "inactive".equals(x) ? Set.of(Flag.INACTIVE) : Set.of(), boolCtl::setFlags);
                    yield boolCtl;
                }
                default -> throw new RuntimeException("Unknown control type: "+type);
            };
            result.put( cntrl.getName(), cntrl );
        }
        return result;
    }

    private <T> void extract(Map<String,String> params, String key, Function<String,T> converter, Consumer<T> c) {

        if ( params.containsKey(key ) ) {
            final T value = converter.apply(params.get(key));
            c.accept(value );
        }
    }

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        SwingUtilities.invokeAndWait(Main::new);
    }
}
