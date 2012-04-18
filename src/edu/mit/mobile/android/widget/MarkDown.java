package edu.mit.mobile.android.widget;

import android.text.Html;
import android.text.Spanned;

import com.petebevin.markdown.MarkdownProcessor;

public class MarkDown {
    /**
     * Function to take Markdown formatted text and return the stylized text.
     * 
     * @param text The text to be formatted.
     * @return word The formatted text as a Spanned object.
     */
    public static Spanned convertText(String text){
        MarkdownProcessor m = new MarkdownProcessor();
        Spanned word = Html.fromHtml(m.markdown(text));
        
        return word;
    }
}
