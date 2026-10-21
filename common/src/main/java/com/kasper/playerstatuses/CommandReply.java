package com.kasper.playerstatuses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A command's answer as chat lines made of spans: text with a role, which decides its colour. Each
 * platform turns the spans into its own chat text; the console and the tests read the plain text.
 */
public final class CommandReply {

    public enum Style {
        PLAIN, TITLE, LABEL, VALUE, OK, WARN, ERROR, MUTED
    }

    /** A piece of a line. */
    public static final class Span {
        private final String text;
        private final Style style;

        public Span(String text, Style style) {
            this.text = text == null ? "" : text;
            this.style = style == null ? Style.PLAIN : style;
        }

        public String text() {
            return text;
        }

        public Style style() {
            return style;
        }
    }

    /** One chat line. */
    public static final class Line {
        private final List<Span> spans;

        Line(List<Span> spans) {
            this.spans = Collections.unmodifiableList(new ArrayList<>(spans));
        }

        public List<Span> spans() {
            return spans;
        }

        public String plain() {
            StringBuilder b = new StringBuilder();
            for (Span s : spans) {
                b.append(s.text());
            }
            return b.toString();
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private boolean success = true;

    /** Starts a new line made of the given text/style pairs. */
    public CommandReply line(Object... textAndStyle) {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i + 1 < textAndStyle.length; i += 2) {
            spans.add(new Span(String.valueOf(textAndStyle[i]), (Style) textAndStyle[i + 1]));
        }
        lines.add(new Line(spans));
        return this;
    }

    /** A line in one style. */
    public CommandReply text(String text, Style style) {
        return line(text, style);
    }

    public CommandReply failed() {
        success = false;
        return this;
    }

    public boolean success() {
        return success;
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** Every line, one per row. */
    public String plain() {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(l.plain());
        }
        return b.toString();
    }
}
