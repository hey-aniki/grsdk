package org.cocos2dx.lib;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

public class WXRecorder {
    private static final String TAG = "WRecorder";
    private static final boolean VERBOSE = true;
    private static final File OUTPUT_DIR = new File("/mnt/sdcard");

    private static final String MIME_TYPE = "video/avc";
    private static final int BIT_RATE = 4000000;
    private static final int FRAMES_PER_SECOND = 4;
    private static final int IFRAME_INTERVAL = 5;

    private static final int NUM_FRAMES = 8;

    // "live" state during recording
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private long mFakePts;

    private long mToWait;
    private long mWaitingTime;

	private static WXRecorder w;
    private int mWidth;
    private int mHeight;
    private int mCnt;
    private boolean mOver;
    private RecThread rec;

	private WXRecorder() {
	}

	public static WXRecorder getInstance() {
		if(w == null){
			w = new WXRecorder();
		}
		return w;
	}

	public void init() {
		EGLDisplay display = EGL14.eglGetCurrentDisplay();
		EGLSurface surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
		int res[] = new int[2];
		EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, res, 0);
		EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, res, 1);
		mWidth = res[0];
		mHeight = res[1];
		rec = new RecThread(mWidth, mHeight, 20);
		rec.mRestart = true;
		rec.start();
		mCnt = 0;
		mOver = false;
        mWaitingTime = System.nanoTime();
        mToWait = (long) ((1.0f / 20) * 1000000L);
	}

    public void drawBegin() {
        if(mCnt > 800){
            if(!mOver){
            	rec.mStop = true;
            	rec.mFrameAvailable = false;
                mOver = true;
            }
            return;
        }
        mCnt = mCnt + 1;
    }

    public void drawEnd() {
        if(mOver)
            return;
        if(System.nanoTime() - mWaitingTime > mToWait){
            synchronized(rec.pixelBuf){
                rec.pixelBuf.rewind();
                GLES20.glReadPixels(0, 0, mWidth, mHeight,
                         GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rec.pixelBuf);
            }
            rec.mFrameAvailable = true;
            mWaitingTime = System.nanoTime();
        }
    }

    public static class RecThread extends Thread {
    	private static final String TAG = "WRecorder";
        private static final boolean VERBOSE = true;
        private static final File OUTPUT_DIR = new File("/mnt/sdcard");

        private static final String MIME_TYPE = "video/avc";
        private static final int BIT_RATE = 4000000;
        private static final int FRAMES_PER_SECOND = 4;
        private static final int IFRAME_INTERVAL = 5;

        // "live" state during recording
        private MediaCodec.BufferInfo mBufferInfo;
        private MediaCodec mEncoder;
        private MediaMuxer mMuxer;
        private Surface mInputSurface;
        private int mTrackIndex;
        private boolean mMuxerStarted;


        private int mWidth;
        private int mHeight;
        private ByteBuffer pixelBuf;
        private ByteBuffer pixelBufCopy;
        private ByteBuffer pixelBufTemp;
        private byte bytes[];

        private boolean mRestart;
        public boolean mStop;
        private boolean mRecording;
        public boolean mFrameAvailable;
        private long mWaited;
        private long mStartTime;




        public RecThread(int width, int height, float fps) {
        	mWidth = width;
        	mHeight = height;
			pixelBuf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
			pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
			pixelBufTemp = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
			pixelBufTemp.order(ByteOrder.LITTLE_ENDIAN);
			pixelBufCopy= ByteBuffer.allocateDirect(mWidth * mHeight * 4);
			pixelBufCopy.order(ByteOrder.LITTLE_ENDIAN);
            bytes = new byte[mWidth * 4];
            mFrameAvailable = false;
            mStartTime = 0;
            mWaited = 0;
        }

        public void run(){
        	while(true) {
        		if(mRestart){
                    mRestart = false;
        			stopRecord();
        			startRecord();
        		} else if(mStop) {
        			stopRecord();
        			break;
        		} else if(mRecording) {
        			if(mFrameAvailable){
                        if(mStartTime == 0){
                            mStartTime = System.nanoTime();
                            mWaited = 0;
                        } else {
                            mWaited = System.nanoTime() - mStartTime;
                        }
                        Log.e("WXRecorder", "doing rec");
            			generateFrame();
            			drainEncoder(false);
                        mFrameAvailable = false;
            			continue;
        			}
        			//try {
					//	sleep(1);
					//} catch (InterruptedException e) {
					//	// TODO Auto-generated catch block
					//	e.printStackTrace();
					//}
        		}
        	}
        }

        public void startRecord() {
        	File outputFile = new File(OUTPUT_DIR, "test_wrecorder.mp4");
            try {
				prepareEncoder(outputFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            mRecording = true;
        }

        public void stopRecord() {
			if(mRecording){
                Log.e("WXRecorder","stop");
	        	drainEncoder(true);
	            releaseEncoder();
	            mRecording = false;
			}
        }

        private void releaseEncoder() {
            if (VERBOSE) Log.d(TAG, "releasing encoder objects");
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
                mEncoder = null;
            }
            if (mInputSurface != null) {
                mInputSurface.release();
                mInputSurface = null;
            }
            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }

        private void prepareEncoder(File outputFile) throws IOException {
            Log.e("prepareEncoder", "1");
            mBufferInfo = new MediaCodec.BufferInfo();

            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

            // Set some properties. Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMES_PER_SECOND);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            if (VERBOSE) Log.d(TAG, "format: " + format);

            // Create a MediaCodec encoder, and configure it with our format. Get a Surface
            // we can use for input and wrap it with a class that handles the EGL work.
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();

            // Create a MediaMuxer. We can't add the video track and start() the muxer here,
            // because our MediaFormat doesn't have the Magic Goodies. These can only be
            // obtained from the encoder after it has started processing data.
            //
            // We're not actually interested in multiplexing audio. We just want to convert
            // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
            if (VERBOSE) Log.d(TAG, "output will go to " + outputFile);
            mMuxer = new MediaMuxer(outputFile.toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            mTrackIndex = -1;
            mMuxerStarted = false;
        }

        private void drainEncoder(boolean endOfStream) {
            final int TIMEOUT_USEC = 10000;
            if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");

            if (endOfStream) {
                if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
                mEncoder.signalEndOfInputStream();
            }

            ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
            while (true) {
                int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (!endOfStream) {
                        break; // out of while
                    } else {
                        if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = mEncoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (mMuxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    mTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    mMuxerStarted = true;
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus);
                    // let's ignore it
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        mBufferInfo.size = 0;
                    }

                    if (mBufferInfo.size != 0) {
                        if (!mMuxerStarted) {
                            throw new RuntimeException("muxer hasn't started");
                        }

                        // adjust the ByteBuffer values to match BufferInfo
                        encodedData.position(mBufferInfo.offset);
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                        mBufferInfo.presentationTimeUs = mWaited / 1000L;

                        mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                        if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                    }

                    mEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (!endOfStream) {
                            Log.w(TAG, "reached end of stream unexpectedly");
                        } else {
                            if (VERBOSE) Log.d(TAG, "end of stream reached");
                        }
                        break; // out of while
                    }
                }
            }
        }

        private void generateFrame() {
            Canvas canvas = mInputSurface.lockCanvas(null);
            try {
                //pixelBuf.rewind();
            	//GLES20.glReadPixels(0, 0, mWidth, mHeight,
                //         GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
                synchronized(pixelBuf){
                    pixelBuf.rewind();
                    pixelBufCopy.rewind();
                    pixelBufCopy.put(pixelBuf);
                }
                pixelBufCopy.rewind();
                pixelBufTemp.rewind();
                int pos = mWidth * mHeight * 4;
                for(int i = 0; i < mHeight; i++) {
                    pixelBufCopy.get(bytes);
                    pos -= mWidth * 4;
                    pixelBufTemp.position(pos);
                    pixelBufTemp.put(bytes);
                }
                Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                pixelBufTemp.rewind();
                bmp.copyPixelsFromBuffer(pixelBufTemp);
                pixelBufTemp.rewind();
                canvas.drawBitmap(bmp, 0, 0, null);
            } catch(Exception e) {
                Log.e("WRecorder", "frame", e);
            } finally {
                mInputSurface.unlockCanvasAndPost(canvas);
            }
        }
    }
}
