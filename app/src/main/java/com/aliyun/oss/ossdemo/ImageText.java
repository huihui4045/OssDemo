package com.aliyun.oss.ossdemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Administrator on 2015/12/18 0018.
 */
public class ImageText extends LinearLayout {

    private TextView textView;
    private ImageView imageView;

    public ImageText(Context context) {
        super(context);

        String infSer = Context.LAYOUT_INFLATER_SERVICE;
        LayoutInflater inf = (LayoutInflater) getContext().getSystemService(infSer);
        inf.inflate(R.layout.image_text, this, true);

        textView = (TextView) findViewById(R.id.info);
        imageView = (ImageView) findViewById(R.id.image);

    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }
}
