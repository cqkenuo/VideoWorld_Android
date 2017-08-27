package com.lxw.videoworld.app.service;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.lxw.videoworld.app.ui.PlayVideoActivity;
import com.lxw.videoworld.framework.util.Base64Util;
import com.lxw.videoworld.framework.util.ToastUtil;
import com.lxw.videoworld.framework.widget.DownloadTorrentDialog;
import com.lxw.videoworld.framework.widget.LoadingDialog;
import com.xunlei.downloadlib.XLTaskHelper;
import com.xunlei.downloadlib.parameter.TorrentInfo;
import com.xunlei.downloadlib.parameter.XLTaskInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static com.lxw.videoworld.app.config.Constant.PATH_OFFLINE_DOWNLOAD;


/**
 * Created by Zion on 2017/8/23.
 */

public class DownloadManager {
    public static List<Long> taskIds;

    public static long addNormalTask(final Context context, final String link, final boolean isPlayVideo) {
        long taskId;
        try {
            if (link.startsWith("magnet:?") || XLTaskHelper.instance().getFileName(link).endsWith("torrent")) {
                if(XLTaskHelper.instance().getFileName(link).endsWith("torrent")){
                    taskId = XLTaskHelper.instance().addMagentTask(getRealUrl(link), PATH_OFFLINE_DOWNLOAD, null);
                }else {
                    taskId = XLTaskHelper.instance().addMagentTask(link, PATH_OFFLINE_DOWNLOAD, null);
                }
                getDownloadObservable(taskId).subscribe(new Observer<XLTaskInfo>() {
                    Disposable mD = null;
                    LoadingDialog loadingDialog = new LoadingDialog(context);

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        mD = d;
                    }

                    @Override
                    public void onNext(XLTaskInfo xlTaskInfo) {
                        switch (String.valueOf(xlTaskInfo.mTaskStatus)) {
                            case "0":
                                ToastUtil.showMessage("资源已在下载队列中");
                                mD.dispose();
                                break;
                            case "1":
                                if(!loadingDialog.isShowing()){
                                    loadingDialog.show();
                                }
                                break;
                            case "2":
                                if(loadingDialog.isShowing()){
                                    loadingDialog.dismiss();
                                }
                                mD.dispose();
                                String torrentPath = PATH_OFFLINE_DOWNLOAD + XLTaskHelper.instance().getFileName(link);
                                TorrentInfo torrentInfo = XLTaskHelper.instance().getTorrentInfo(torrentPath);
                                torrentInfo.torrentPath = torrentPath;
                                DownloadTorrentDialog dialog = new DownloadTorrentDialog(context, torrentInfo, isPlayVideo);
                                dialog.show();
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });

            } else {
                taskId = XLTaskHelper.instance().addThunderTask(link, PATH_OFFLINE_DOWNLOAD, null);
                String url = XLTaskHelper.instance().getLoclUrl(PATH_OFFLINE_DOWNLOAD +
                        XLTaskHelper.instance().getFileName(link));
                if (isPlayVideo) {
                    Intent intent = new Intent(context, PlayVideoActivity.class);
                    intent.putExtra("url", url);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (taskIds == null) taskIds = new ArrayList<>();
        taskIds.add(taskId);
        return taskId;
    }

    public static long addTorrentTask(String torrentPath, String savePath, int[] indexs) {
        return addTorrentTask(torrentPath, savePath, indexs, -1);
    }

    public static long addTorrentTask(String torrentPath, String savePath, int[] indexs, int index) {
        long taskId;
        try {
            taskId = XLTaskHelper.instance().addTorrentTask(torrentPath, savePath, indexs);
            for(int i = 0; i < indexs.length; i++){
                XLTaskHelper.instance().startDcdn(taskId, indexs[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (taskIds == null) taskIds = new ArrayList<>();
        taskIds.add(taskId);
        return taskId;
    }

    public static Observable getDownloadObservable(final long taskId) {
        return Observable.interval(0, 5, TimeUnit.SECONDS)
                .map(new Function<Long, XLTaskInfo>() {

                    @Override
                    public XLTaskInfo apply(@NonNull Long aLong) throws Exception {
                        return XLTaskHelper.instance().getTaskInfo(taskId);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     *  迅雷thunder://地址与普通url地址转换
     其实迅雷的thunder://地址就是将普通url地址加前缀‘AA’、后缀‘ZZ’，再base64编码后得到的字符串
     * @param url
     * @return
     */
    public static String getRealUrl(String url){
        String realUrl="";
        if(!TextUtils.isEmpty(url)){
            //去掉迅雷地址前缀
            url = url.substring(10, url.length());
            //解密
            realUrl = Base64Util.decodeBase64(url);
            //去掉头AA，尾ZZ
            realUrl = realUrl.substring(2, realUrl.length()-2);
        }
        return realUrl;
    }
}