package com.goldsrc.maker.scripting;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorView extends EditText {

    public interface EditListener {
        void onUserEdit(String text);
    }

    public interface CaretListener {
        void onCaret(int line, int column);
    }

    private static final int COLOR_TEXT = 0xFFD4D4D4;
    private static final int COLOR_COMMENT = 0xFF6A9955;
    private static final int COLOR_STRING = 0xFFCE9178;
    private static final int COLOR_KEYWORD = 0xFF569CD6;
    private static final int COLOR_NATIVE = 0xFF4EC9B0;
    private static final int COLOR_NUMBER = 0xFFB5CEA8;

    private static final Pattern KEYWORD = Pattern.compile(
            "\\b(void|int|float|double|bool|string|if|else|for|while|do|return|break|continue"
                    + "|const|class|namespace|true|false|switch|case|null)\\b");
    private static final Pattern NATIVE_CALL = Pattern.compile("\\bgs_[A-Za-z0-9_]+");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?f?\\b");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    private EditListener editListener;
    private CaretListener caretListener;
    private boolean programmatic = false;
    private boolean highlightScheduled = false;

    public CodeEditorView(Context context) {
        this(context, null);
    }

    public CodeEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setTypeface(Typeface.MONOSPACE);
        setTextSize(13f);
        setTextColor(COLOR_TEXT);
        setBackgroundColor(0xFF1E1E1E);
        setHorizontallyScrolling(true);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (programmatic) {
                    return;
                }
                scheduleHighlight();
                if (editListener != null) {
                    editListener.onUserEdit(s.toString());
                }
            }
        });
    }

    public void setEditListener(EditListener listener) {
        this.editListener = listener;
    }

    public void setCaretListener(CaretListener listener) {
        this.caretListener = listener;
    }

    public void setTextQuiet(String text) {
        programmatic = true;
        setText(text);
        setSelection(text.length());
        programmatic = false;
        scheduleHighlight();
    }

    public String getContent() {
        return getText() == null ? "" : getText().toString();
    }

    private void scheduleHighlight() {
        if (highlightScheduled) {
            return;
        }
        highlightScheduled = true;
        post(new Runnable() {
            @Override
            public void run() {
                highlightScheduled = false;
                applyHighlight();
            }
        });
    }

    private void applyHighlight() {
        Editable editable = getText();
        if (editable == null || editable.length() == 0) {
            return;
        }

        ForegroundColorSpan[] existing = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : existing) {
            editable.removeSpan(span);
        }

        String text = editable.toString();
        int offset = 0;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            highlightLine(editable, line, offset);
            offset += line.length() + 1;
        }
    }

    private void highlightLine(Editable editable, String line, int lineStart) {
        int commentIndex = line.indexOf("//");
        int codeEnd = commentIndex >= 0 ? commentIndex : line.length();

        if (commentIndex >= 0) {
            editable.setSpan(new ForegroundColorSpan(COLOR_COMMENT),
                    lineStart + commentIndex, lineStart + line.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String code = line.substring(0, codeEnd);

        List<int[]> stringRanges = new ArrayList<>();
        Matcher stringMatcher = STRING_LITERAL.matcher(code);
        while (stringMatcher.find()) {
            stringRanges.add(new int[]{stringMatcher.start(), stringMatcher.end()});
            editable.setSpan(new ForegroundColorSpan(COLOR_STRING),
                    lineStart + stringMatcher.start(), lineStart + stringMatcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher keywordMatcher = KEYWORD.matcher(code);
        while (keywordMatcher.find()) {
            if (!inRanges(stringRanges, keywordMatcher.start())) {
                editable.setSpan(new ForegroundColorSpan(COLOR_KEYWORD),
                        lineStart + keywordMatcher.start(), lineStart + keywordMatcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Matcher nativeMatcher = NATIVE_CALL.matcher(code);
        while (nativeMatcher.find()) {
            if (!inRanges(stringRanges, nativeMatcher.start())) {
                editable.setSpan(new ForegroundColorSpan(COLOR_NATIVE),
                        lineStart + nativeMatcher.start(), lineStart + nativeMatcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Matcher numberMatcher = NUMBER.matcher(code);
        while (numberMatcher.find()) {
            if (!inRanges(stringRanges, numberMatcher.start())) {
                editable.setSpan(new ForegroundColorSpan(COLOR_NUMBER),
                        lineStart + numberMatcher.start(), lineStart + numberMatcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static boolean inRanges(List<int[]> ranges, int index) {
        for (int[] range : ranges) {
            if (index >= range[0] && index < range[1]) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (caretListener == null || selStart < 0 || getText() == null) {
            return;
        }
        Layout layout = getLayout();
        if (layout != null && selStart <= getText().length()) {
            int line = layout.getLineForOffset(selStart);
            int column = selStart - layout.getLineStart(line);
            caretListener.onCaret(line + 1, column + 1);
        }
    }
}
