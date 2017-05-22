package com.aliyun.oss.ossdemo;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final String endpoint = "http://oss-cn-beijing.aliyuncs.com";
    private static final String callbackAddress = "http://oss-demo.aliyuncs.com:23450";
    private String bucket = "moluclown";
    //private String stsServer = "http://oss-demo.aliyuncs.com/app-server/sts.php";


    private static final String accessKeyId = "LTAIGyAKs9wl0DP3";
    private static final String accessKeySecret = "eQqc6dexegrHNQpdO12fPTT3CUgUyH";

    //负责所有的界面更新
    private ImageDisplayer ImageDisplayer;
    private UIDispatcher UIDispatcher;

    //OSS的上传下载
    private OssService ossService;

    private int pauseTaskStatus;
    private PauseableUploadTask task;
    private String pauseObject;
    private String pauseLocalFile;
    private static final int TASK_NONE = 1;
    private static final int TASK_PAUSE = 2;
    private static final int TASK_RUNNING = 3;

    private static final int RESULT_UPLOAD_IMAGE = 1;
    private static final int RESULT_PAUSEABLEUPLOAD_IMAGE = 2;


    //初始化一个OssService用来上传下载
    public OssService initOSS(String endpoint, String bucket, ImageDisplayer displayer) {
        //如果希望直接使用accessKey来访问的时候，可以直接使用OSSPlainTextAKSKCredentialProvider来鉴权。
        OSSCredentialProvider credentialProvider = new OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret);

        //使用自己的获取STSToken的类
        //OSSCredentialProvider credentialProvider = new STSGetter(stsServer);

        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次

        OSS oss = new OSSClient(getApplicationContext(), endpoint, credentialProvider, conf);

        return new OssService(oss, bucket, displayer);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        UIDispatcher = new UIDispatcher(Looper.getMainLooper());
        ossService = initOSS(endpoint, bucket, ImageDisplayer);
        //设置上传的callback地址，目前暂时只支持putObject的回调
        ossService.setCallbackAddress(callbackAddress);

        ImageView imageView = (ImageView) findViewById(R.id.imageView);
        ImageDisplayer = new ImageDisplayer(imageView);

        Button set = (Button) findViewById(R.id.set);
        set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //stsServer = ((EditText)findViewById(R.id.sts_server)).getText().toString();
                bucket = ((EditText)findViewById(R.id.bucket)).getText().toString();
                ossService = initOSS(endpoint, bucket, ImageDisplayer);
                //设置上传的callback地址，目前暂时只支持putObject的回调
                ossService.setCallbackAddress(callbackAddress);
                displayToast("设置成功");
            }
        });


        Button upload = (Button) findViewById(R.id.upload);
        upload.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                startActivityForResult(i, RESULT_UPLOAD_IMAGE);
            }
        });

        Button download = (Button) findViewById(R.id.download);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.edit_text);
                String objectName = editText.getText().toString();
                ossService.asyncGetImage(objectName, getGetCallback());
            }
        });

        Button multipart_upload = (Button) findViewById(R.id.multipart_upload);
        final Button multipart_pause = (Button) findViewById(R.id.multipart_pause);
        final Button multipart_resume = (Button) findViewById(R.id.multipart_resume);

        //刚开始的时候是无法恢复上传的
        multipart_resume.setEnabled(false);
        multipart_pause.setEnabled(false);

        multipart_upload.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //为了简单化，这里只会同时运行一个断点上传的任务
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                startActivityForResult(i, RESULT_PAUSEABLEUPLOAD_IMAGE);

            }
        });


        multipart_pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pauseTaskStatus == TASK_RUNNING) {
                    Log.d("MultiPartTask", "Pasue");
                    task.pause();
                    task = null;
                    pauseTaskStatus = TASK_PAUSE;
                    multipart_resume.setEnabled(true);
                    multipart_pause.setEnabled(false);
                }
                else {
                    Log.d("MultiPartTask", "PasueFail");
                }
            }
        });

        multipart_resume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pauseTaskStatus == TASK_PAUSE) {
                    Log.d("MultiPartTask", "Resume");
                    task = ossService.asyncMultiPartUpload(pauseObject, pauseLocalFile, getMultiPartCallback().addCallback(new Runnable() {
                        @Override
                        public void run() {
                            pauseTaskStatus = TASK_NONE;
                            multipart_resume.setEnabled(false);
                            multipart_pause.setEnabled(false);
                            task = null;
                        }
                    }), new ProgressCallbackFactory<PauseableUploadRequest>().get());
                    pauseTaskStatus = TASK_RUNNING;
                    multipart_resume.setEnabled(false);
                    multipart_pause.setEnabled(true);
                }
                else {
                    Log.d("MultiPartTask", "ResumeFail");
                }
            }
        });

        Button more = (Button) findViewById(R.id.more);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.edit_text);
                String objectName = editText.getText().toString();
                Intent intent = new Intent(MainActivity.this, ImageActivity.class);
                intent.putExtra("ObjectName", objectName);
                intent.putExtra("Bucket", bucket);
               // intent.putExtra("STSServer", stsServer);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == RESULT_UPLOAD_IMAGE || requestCode == RESULT_PAUSEABLEUPLOAD_IMAGE) && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            Log.d("PickPicture", picturePath);
            cursor.close();

            try {
                Bitmap bm = ImageDisplayer.autoResizeFromLocalFile(picturePath);
                displayImage(bm);

                File file = new File(picturePath);
                displayInfo("文件: " + picturePath + "\n大小: " + String.valueOf(file.length()));
            }
            catch (IOException e) {
                e.printStackTrace();
                displayInfo(e.toString());
            }

            //根据操作不同完成普通上传或者断点上传
            if (requestCode == RESULT_UPLOAD_IMAGE) {
                final EditText editText = (EditText) findViewById(R.id.edit_text);
                String objectName = editText.getText().toString();

                ossService.asyncPutImage("image/b.png", picturePath, getPutCallback(), new ProgressCallbackFactory<PutObjectRequest>().get());
            }
            else {
                Log.d("MultiPartUpload", "Start");
                EditText editText = (EditText) findViewById(R.id.edit_text);
                String objectName = editText.getText().toString();
                final Button multipart_pause = (Button) findViewById(R.id.multipart_pause);
                final Button multipart_resume = (Button) findViewById(R.id.multipart_resume);
                multipart_resume.setEnabled(false);
                multipart_pause.setEnabled(true);

                task = ossService.asyncMultiPartUpload("image/a.png", picturePath, getMultiPartCallback().addCallback(new Runnable() {
                    @Override
                    public void run() {
                        pauseTaskStatus = TASK_NONE;
                        multipart_resume.setEnabled(false);
                        multipart_pause.setEnabled(false);
                        task = null;
                    }
                }), new ProgressCallbackFactory<PauseableUploadRequest>().get());
                pauseTaskStatus = TASK_RUNNING;
                pauseObject = objectName;
                pauseLocalFile = picturePath;
            }
        }
    }

    public void displayImage(Bitmap bm) {
        ((ImageView) findViewById(R.id.imageView)).setImageBitmap(bm);
    }

    public void displayInfo(String info) {
        ((TextView) findViewById(R.id.output_info)).setText(info);
    }

    public void updateProgress(int pro) {
        ((ProgressBar) findViewById(R.id.bar)).setProgress(pro);
    }

    public void displayToast(String info) {
        Toast.makeText(this, info, Toast.LENGTH_SHORT).show();
    }
    public void displayDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).show();
    }

    private class ProgressCallbackFactory<T> {
        public UIProgressCallback<T> get() {
            return new UIProgressCallback<T>(UIDispatcher) {
                @Override
                public void onProgress(T request, long currentSize, long totalSize) {
                    final int progress = (int) (100 * currentSize / totalSize);
                    addCallback(new Runnable() {
                        @Override
                        public void run() {
                            updateProgress(progress);
                            displayInfo("进度: " + String.valueOf(progress) + "%");
                        }
                    });
                    super.onProgress(request, currentSize, totalSize);
                }
            };
        }
    }

    public UICallback<GetObjectRequest, GetObjectResult> getGetCallback() {
        return new UICallback<GetObjectRequest, GetObjectResult>(UIDispatcher) {
            @Override
            public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                // 请求成功
                InputStream inputStream = result.getObjectContent();
                //重载InputStream来获取读取进度信息
                ProgressInputStream progressStream = new ProgressInputStream(inputStream,
                        new ProgressCallbackFactory<GetObjectRequest>().get(),
                        result.getContentLength());
                try {
                    //需要根据对应的View大小来自适应缩放
                    final Bitmap bm = ImageDisplayer.autoResizeFromStream(progressStream);
                    final String object = request.getObjectKey();
                    final String requestid = result.getRequestId();
                    addCallback(new Runnable() {
                        @Override
                        public void run() {
                            displayImage(bm);
                            displayToast("下载成功");
                            displayInfo(String.format("Bucket: %s\nObject: %s\nRequestId: %s",
                                    bucket,
                                    object,
                                    requestid));
                        }
                    }, null);
                    super.onSuccess(request, result);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                String info = "";
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    info = clientExcepion.toString();
                }
                if (serviceException != null) {
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
                        displayDialog("下载失败", outputinfo);
                        displayInfo(outputinfo);
                    }
                });
                super.onFailure(request, clientExcepion, serviceException);
            }
        };
    }


    public UICallback<PutObjectRequest, PutObjectResult> getPutCallback() {
        return new UICallback<PutObjectRequest, PutObjectResult>(UIDispatcher) {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                Log.d("PutObject", "UploadSuccess");

                Log.d("ETag", result.getETag());
                Log.d("RequestId", result.getRequestId());
                final String object = request.getObjectKey();
                final String ETag = result.getETag();
                final String requestid = result.getRequestId();
                final String callback = result.getServerCallbackReturnBody();

                addCallback(new Runnable() {
                    @Override
                    public void run() {
                        displayToast("上传成功");
                        displayInfo(String.format("Bucket: %s\nObject: %s\nETag: %s\nRequestId: %s\nCallback: %s",
                                bucket,
                                object,
                                ETag,
                                requestid,
                                callback));
                    }
                }, null);
                super.onSuccess(request, result);
            }

            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                String info = "";
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    info = clientExcepion.toString();
                }
                if (serviceException != null) {
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
                        displayDialog("上传失败", outputinfo);
                        displayInfo(outputinfo);
                    }
                });
                onFailure(request, clientExcepion, serviceException);
            }
        };
    }

    public UICallback<PauseableUploadRequest, PauseableUploadResult> getMultiPartCallback() {
        return new UICallback<PauseableUploadRequest, PauseableUploadResult> (UIDispatcher) {
            //上传成功
            @Override
            public void onSuccess(PauseableUploadRequest request, PauseableUploadResult result) {
                Log.d("PutObject", "UploadSuccess");

                Log.d("ETag", result.getETag());
                Log.d("RequestId", result.getRequestId());
                final String object = request.getObjectKey();
                final String ETag = result.getETag();
                final String requestid = result.getRequestId();

                addCallback(new Runnable() {
                    @Override
                    public void run() {
                        displayToast("断点上传成功");
                        displayInfo("Bucket: " + bucket + "\nObject: " + object + "\nETag: " + ETag + "\nRequestId: " + requestid);
                    }
                }, null);
                super.onSuccess(request, result);
            }

            //上传失败
            @Override
            public void onFailure(PauseableUploadRequest request, ClientException clientExcepion, ServiceException serviceException) {
                String info = "";
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    info = clientExcepion.toString();
                }
                if (serviceException != null) {
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
                        displayDialog("上传失败", outputinfo);
                        displayInfo(outputinfo);
                    }
                });
                super.onFailure(request, clientExcepion, serviceException);
            }
        };
    }

}
