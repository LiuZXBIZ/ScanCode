package com.lenaeon.scancode.zxing.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Created by 刘红亮 on 2015/7/23 22:30.
 */
public class BitmapUtil {

    /**
     * 根据给定的宽度和高度动态计算图片压缩比率
     *
     * @param options   Bitmap配置文件
     * @param reqWidth  需要压缩到的宽度
     * @param reqHeight 需要压缩到的高度
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 源图片的高度和宽度
        final int height = options.outHeight;
        final int width = options.outWidth;
        //压缩当前图片占用内存不超过应用可用内存的3/4
        //ARGB_8888  一个像素占用4个字节
        //1兆字节(mb)=1048576字节(b)
        while (reqHeight * reqWidth * 4 > AppliationUtil.FREE_MEMORY * 1048576 / 4 * 3) {
            reqHeight -= 50;
            reqWidth -= 50;
        }
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            // 计算出实际宽高和目标宽高的比率
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            // 选择宽和高中最小的比率作为inSampleSize的值，这样可以保证最终图片的宽和高
            // 一定都会大于等于目标的宽和高。
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        if (inSampleSize <= 0) return 1;
        Log.e("hongliang", "inSampleSize=" + inSampleSize);
        return inSampleSize;
    }

    /**
     * 将图片根据压缩比压缩成固定宽高的Bitmap，实际解析的图片大小可能和#reqWidth、#reqHeight不一样。
     *
     * @param imgPath   图片地址
     * @param reqWidth  需要压缩到的宽度
     * @param reqHeight 需要压缩到的高度
     * @return Bitmap
     */
    public static Bitmap decodeBitmapFromPath(String imgPath, int reqWidth, int reqHeight) {

        /*BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 先获取原大小
        BitmapFactory.decodeFile(imgPath, options);
        options.inJustDecodeBounds = false; // 获取新的大小
        int sampleSize = (int) (options.outHeight / (float) 100);
        if (sampleSize <= 0)
            sampleSize = 1;
        options.inSampleSize = sampleSize;
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath, options);*/


        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imgPath, options);
        // 调用上面定义的方法计算inSampleSize值
        options.inJustDecodeBounds = false; // 获取新的大小
        int sampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inSampleSize = sampleSize;
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath, options);

        return bitmap;
    }
}