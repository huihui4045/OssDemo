package com.aliyun.oss.ossdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Administrator on 2015/12/18 0018.
 */
public class ImageAdapter extends BaseAdapter {
    private Context mContext;
    private Map<Integer, Bitmap> imgMap = new ConcurrentHashMap<>();
    private Map<Integer, String> textMap = new ConcurrentHashMap<>();

    public ImageAdapter(Context c) {
        mContext = c;
    }

    public Map<Integer, Bitmap> getImgMap() {
        return imgMap;
    }

    public Map<Integer, String> getTextMap() {
        return textMap;
    }

    public int getCount() {
        Log.d("GetSize", String.valueOf(imgMap.size()));
        return imgMap.size();
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.d("GetView", "Begin");
        Log.d("Position", String.valueOf(position));

        ImageText imageText;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageText = new ImageText(mContext);
           // imageText.setLayoutParams(new GridView.LayoutParams(85, 85));

        } else {
            imageText = (ImageText) convertView;
        }

        if (imgMap.containsKey(position)) {
            Bitmap bm = imgMap.get(position);

            if (bm == null) {
                Log.d("GetBitmap", "Fail");
            }

            imageText.getImageView().setImageBitmap(bm);
        }
        else {
            Log.d("GetImage", "NotExist");
        }
        imageText.getTextView().setText(textMap.get(position));
        return imageText;
    }

}
