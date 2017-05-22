package com.aliyun.oss.ossdemo;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;

import java.io.IOException;
import java.io.InputStream;

public class ImageActivity extends AppCompatActivity {
    private OSS oss;
    private String bucket;
    private ImageAdapter adapter;
    private ImageService imageService = new ImageService();
    private UIDispatcher uiDispatcher;
    private final static String imgEndpoint = "http://img-cn-hangzhou.aliyuncs.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        String objectName = intent.getStringExtra("ObjectName");
        bucket = intent.getStringExtra("Bucket");
        String stsServer = intent.getStringExtra("STSServer");

        Log.d("Object", objectName);
        Log.d("Bucket", bucket);

        OSSCredentialProvider credentialProvider = new STSGetter(stsServer);

        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次

        oss = new OSSClient(getApplicationContext(), imgEndpoint, credentialProvider, conf);

        uiDispatcher = new UIDispatcher(Looper.getMainLooper());

        ListView gv = (ListView) findViewById(R.id.listview);
        adapter = new ImageAdapter(this);
        gv.setAdapter(adapter);

        getImage(imageService.textWatermark(objectName, "OSS测试", 100), 0, "右下角文字水印，大小100");
        getImage(imageService.resize(objectName, 100, 100), 1, "缩放到100*100");
        getImage(imageService.crop(objectName, 100, 100, 9), 2, "右下角裁剪100*100");
        getImage(imageService.rotate(objectName, 90), 3, "旋转90度");
    }


    public void getImage(final String object, final Integer index, final String method) {
        GetObjectRequest get = new GetObjectRequest(bucket, object);
        Log.d("Object", object);

        OSSAsyncTask task = oss.asyncGetObejct(get, new UICallback<GetObjectRequest, GetObjectResult>(uiDispatcher) {
            @Override
            public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                // 请求成功
                InputStream inputStream = result.getObjectContent();
                Log.d("GetImage", object);
                Log.d("Index", String.valueOf(index));

                try {
                    //防止超过显示的最大限制
                    adapter.getImgMap().put(index, new ImageDisplayer(1000, 1000).autoResizeFromStream(inputStream));
                    adapter.getTextMap().put(index, method + "\n" + object);

                    //需要根据对应的View大小来自适应缩放
                    addCallback(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    }, null);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                super.onSuccess(request,result);
            }

            @Override
            public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                String info = "";
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    info = clientExcepion.toString();
                }
                if (serviceException != null) {
                    serviceException.printStackTrace();
                    // 服务异常
                    Log.e("ErrorCode", serviceException.getErrorCode());
                    Log.e("RequestId", serviceException.getRequestId());
                    Log.e("HostId", serviceException.getHostId());
                    Log.e("RawMessage", serviceException.getRawMessage());
                    info = serviceException.toString();
                }
                final String outputinfo = new String(info);
                addCallback(null, new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(ImageActivity.this).setTitle("下载失败").setMessage(outputinfo).show();
                    }
                });
                super.onFailure(request, clientExcepion, serviceException);
            }
        });
    }

}
