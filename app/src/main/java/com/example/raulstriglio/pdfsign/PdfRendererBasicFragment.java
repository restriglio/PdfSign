/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.raulstriglio.pdfsign;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.ajithvgiri.canvaslibrary.CanvasView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;

/**
 * This fragment has a big {@ImageView} that shows PDF pages, and 2
 * {@link Button}s to move between pages. We use a
 * {@link PdfRenderer} to render PDF pages as
 * {@link Bitmap}s.
 */
public class PdfRendererBasicFragment extends Fragment implements View.OnClickListener {


    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;

    /**
     * Key string for saving the state of current page index.
     */
    private static final String STATE_CURRENT_PAGE_INDEX = "current_page_index";

    /**
     * The filename of the PDF.
     */
    private static final String FILENAME = "sample.pdf";

    /**
     * File descriptor of the PDF.
     */
    private ParcelFileDescriptor mFileDescriptor;

    /**
     * {@link PdfRenderer} to render the PDF.
     */
    private PdfRenderer mPdfRenderer;

    /**
     * Page that is currently shown on the screen.
     */
    private PdfRenderer.Page mCurrentPage;

    /**
     * {@link ImageView} that shows a PDF page as a {@link Bitmap}
     */
    private ImageCanvasView mImageView;

    /**
     * {@link Button} to move to the previous page.
     */
    private Button mButtonPrevious;
    private Button open_file;

    /**
     * {@link Button} to move to the next page.
     */
    private Button mButtonNext;

    /**
     * PDF page index
     */
    private int mPageIndex;
    private Uri uri;

    private HashMap<Integer, PdfRenderer.Page> integerPageHashMap;
    private HashMap<Integer, ImageCanvasView> integerImageViewHashMap;

    ConstraintLayout image_parent;

    boolean renderOpen = false;

    public PdfRendererBasicFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pdf_renderer_basic, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Retain view references.
        mImageView = (ImageCanvasView) view.findViewById(R.id.image);
        mButtonPrevious = (Button) view.findViewById(R.id.previous);
        mButtonNext = (Button) view.findViewById(R.id.next);
        open_file = (Button) view.findViewById(R.id.open_file);
        image_parent = (ConstraintLayout) view.findViewById(R.id.image_parent);
        // Bind events.
        mButtonPrevious.setOnClickListener(this);
        mButtonNext.setOnClickListener(this);

        mPageIndex = 0;
        // If there is a savedInstanceState (screen orientations, etc.), we restore the page index.
        if (null != savedInstanceState) {
            mPageIndex = savedInstanceState.getInt(STATE_CURRENT_PAGE_INDEX, 0);
        }

        open_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickFile();
            }
        });
    }

    public void pickFile() {

        //checkLocationPermission();
        int permissionCheck = ContextCompat.checkSelfPermission(getActivity(),
                READ_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{READ_EXTERNAL_STORAGE},
                    PERMISSION_CODE
            );

            return;
        }

        permissionCheck = ContextCompat.checkSelfPermission(getActivity(),
                WRITE_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{WRITE_EXTERNAL_STORAGE},
                    PERMISSION_CODE
            );

            return;
        }

        launchPicker();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        try {

            if (renderOpen) {
                closeRenderer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != mCurrentPage) {
            outState.putInt(STATE_CURRENT_PAGE_INDEX, mCurrentPage.getIndex());
        }
    }


    /**
     * Closes the {@link PdfRenderer} and related resources.
     *
     * @throws IOException When the PDF file cannot be closed.
     */
    private void closeRenderer() throws IOException {
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }
        mPdfRenderer.close();
        mFileDescriptor.close();
    }

    public void drawSign(ViewGroup view) {
        CanvasView canvasView = new CanvasView(getActivity());
        view.addView(canvasView);
    }


    /**
     * Shows the specified page of PDF to the screen.
     *
     * @param index The page index.
     */
    private void showPage(int index) {
        if (mPdfRenderer.getPageCount() <= index) {
            return;
        }
        // Make sure to close the current page before opening another one.
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }

        if (integerPageHashMap == null) {
            integerPageHashMap = new HashMap<>();
        }

        if (integerImageViewHashMap == null) {
            integerImageViewHashMap = new HashMap<>();
        }

        image_parent.removeAllViews();
        if (integerPageHashMap.get(index) != null && integerImageViewHashMap.get(index) != null) {

            mCurrentPage = mPdfRenderer.openPage(index);
            mImageView = integerImageViewHashMap.get(index);
            mImageView.setImageBitmap(mImageView.mBitmap);

            ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD,
                    ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD);

            layoutParams.bottomToBottom = image_parent.getId();
            layoutParams.endToEnd = image_parent.getId();
            layoutParams.startToStart = image_parent.getId();
            layoutParams.topToTop = image_parent.getId();

            mImageView.setLayoutParams(layoutParams);
            image_parent.addView(mImageView);

            ConstraintSet constraints = new ConstraintSet();
            constraints.clone(image_parent);
            constraints.constrainDefaultHeight(mImageView.getId(), ConstraintSet.MATCH_CONSTRAINT);
            constraints.constrainDefaultWidth(mImageView.getId(), ConstraintSet.MATCH_CONSTRAINT);
            constraints.applyTo(image_parent);


            updateUi();

        } else {

            mCurrentPage = mPdfRenderer.openPage(index);

            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            Bitmap bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(),
                    Bitmap.Config.ARGB_8888);
            mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            mImageView = new ImageCanvasView(getActivity());
            ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD,
                    ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD);

            layoutParams.bottomToBottom = image_parent.getId();
            layoutParams.endToEnd = image_parent.getId();
            layoutParams.startToStart = image_parent.getId();
            layoutParams.topToTop = image_parent.getId();

            mImageView.setLayoutParams(layoutParams);
            mImageView.mBitmap = bitmap;
            mImageView.mCanvas = new Canvas(bitmap);
            mImageView.setImageBitmap(bitmap);
            image_parent.addView(mImageView);
            integerPageHashMap.put(index, mCurrentPage);
            integerImageViewHashMap.put(index, mImageView);

            ConstraintSet constraints = new ConstraintSet();
            constraints.clone(image_parent);
            constraints.constrainDefaultHeight(mImageView.getId(), ConstraintSet.MATCH_CONSTRAINT);
            constraints.constrainDefaultWidth(mImageView.getId(), ConstraintSet.MATCH_CONSTRAINT);
            constraints.applyTo(image_parent);

            updateUi();
        }
    }

    /**
     * Updates the state of 2 control buttons in response to the current page index.
     */
    private void updateUi() {
        int index = mCurrentPage.getIndex();
        int pageCount = mPdfRenderer.getPageCount();
        mButtonPrevious.setEnabled(0 != index);
        mButtonNext.setEnabled(index + 1 < pageCount);
        getActivity().setTitle(getString(R.string.app_name_with_index, index + 1, pageCount));
    }

    /**
     * Gets the number of pages in the PDF. This method is marked as public for testing.
     *
     * @return The number of pages.
     */
    public int getPageCount() {
        return mPdfRenderer.getPageCount();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.previous: {
                // Move to the previous page
                showPage(mCurrentPage.getIndex() - 1);
                break;
            }
            case R.id.next: {
                // Move to the next page
                showPage(mCurrentPage.getIndex() + 1);
                break;
            }
        }
    }

    public void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            //alert user that file manager not working
            Toast.makeText(getContext(), "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            uri = data.getData();

            try {
                openRenderer(getActivity());
                showPage(mPageIndex);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    /**
     * Sets up a {@link PdfRenderer} and related resources.
     */
    private void openRenderer(Context context) throws IOException {
        // In this sample, we read a PDF from the assets directory.

        /*String pdfFileName = getFileName(uri);

        String path = uri.getPath(); // "/mnt/sdcard/FileName.mp3"
        //File file;
        //file = new File(new URI(path));
        File file = new File(pdfFileName);
        if (!file.exists()) {
            // Since PdfRenderer cannot handle the compressed asset file directly, we copy it into
            // the cache directory.
           // InputStream asset = context.openFileInput(pdfFileName);

            AssetManager am = getContext().getAssets();
            InputStream is = am.open(path);

            FileOutputStream output = new FileOutputStream(file);
            final byte[] buffer = new byte[1024];
            int size;
            while ((size = is.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            is.close();
            output.close();
        }*/

        String pdfFileName = getFileName(uri);
        // In this sample, we read a PDF from the assets directory.

        //File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + filname +".jpg");
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, pdfFileName);
       /* try {
            // Make sure the Pictures directory exists.
            path.mkdirs();

            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        // This is the PdfRenderer we use to render the PDF.
        if (mFileDescriptor != null) {
            mPdfRenderer = new PdfRenderer(mFileDescriptor);
        }

        renderOpen = true;
    }

}
