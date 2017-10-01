package ai.elimu.appstore.synchronization;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import ai.elimu.appstore.BaseApplication;
import ai.elimu.appstore.BuildConfig;
import ai.elimu.appstore.R;
import ai.elimu.appstore.dao.ApplicationVersionDao;
import ai.elimu.appstore.model.Application;
import ai.elimu.appstore.model.ApplicationVersion;
import ai.elimu.appstore.util.ChecksumHelper;
import ai.elimu.appstore.util.DeviceInfoHelper;
import ai.elimu.appstore.util.UserPrefsHelper;
import ai.elimu.model.enums.admin.ApplicationStatus;
import timber.log.Timber;

public class AppListArrayAdapter extends ArrayAdapter<Application> {

    private Context context;

    private List<Application> applications;

    private ApplicationVersionDao applicationVersionDao;

    static class ViewHolder {
        TextView textViewPackageName;
        TextView textViewVersion;
        Button buttonDownload;
        Button buttonInstall;
        ProgressBar progressBarDownloadProgress;
        TextView textViewDownloadProgress;
    }

    public AppListArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<Application> applications) {
        super(context, resource, applications);
        Timber.i("AppListArrayAdapter");

        this.context = context;
        this.applications = applications;

        BaseApplication baseApplication = (BaseApplication) context.getApplicationContext();
        applicationVersionDao = baseApplication.getDaoSession().getApplicationVersionDao();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Timber.d("getView");

        final Application application = applications.get(position);

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View listItem = layoutInflater.inflate(R.layout.activity_app_list_item, parent, false);

        final ViewHolder viewHolder = new ViewHolder();
        viewHolder.textViewPackageName = listItem.findViewById(R.id.textViewPackageName);
        viewHolder.textViewVersion = listItem.findViewById(R.id.textViewVersion);
        viewHolder.buttonDownload = listItem.findViewById(R.id.buttonDownload);
        viewHolder.buttonInstall = listItem.findViewById(R.id.buttonInstall);
        viewHolder.progressBarDownloadProgress = listItem.findViewById(R.id.progressBarDownloadProgress);
        viewHolder.textViewDownloadProgress = listItem.findViewById(R.id.textViewDownloadProgress);

        viewHolder.textViewPackageName.setText(application.getPackageName());

        if (application.getApplicationStatus() != ApplicationStatus.ACTIVE) {
            // Do not allow APK download
            viewHolder.textViewVersion.setText("ApplicationStatus: " + application.getApplicationStatus());
            viewHolder.buttonDownload.setEnabled(false);
            // TODO: hide applications that are not active?
        } else {
            // Fetch the latest APK version
            List<ApplicationVersion> applicationVersions = applicationVersionDao.queryBuilder()
                    .where(ApplicationVersionDao.Properties.ApplicationId.eq(application.getId()))
                    .list();
            final ApplicationVersion applicationVersion = applicationVersions.get(0);

            viewHolder.textViewVersion.setText(context.getText(R.string.version) + ": " + applicationVersion.getVersionCode());

            // Check if the APK file has already been downloaded to the SD card
            String language = UserPrefsHelper.getLocale(context).getLanguage();
            String fileName = applicationVersion.getApplication().getPackageName() + "-" + applicationVersion.getVersionCode() + ".apk";
            File apkDirectory = new File(Environment.getExternalStorageDirectory() + "/.elimu-ai/appstore/apks/" + language);
            File existingApkFile = new File(apkDirectory, fileName);
            Timber.i("existingApkFile: " + existingApkFile);
            Timber.i("existingApkFile.exists(): " + existingApkFile.exists());
            if (existingApkFile.exists()) {
                viewHolder.buttonDownload.setVisibility(View.GONE);
                viewHolder.buttonInstall.setVisibility(View.VISIBLE);
            }

            // Check if the APK file has already been installed
            PackageManager packageManager = context.getPackageManager();
            boolean isAppInstalled = true;
            try {
                packageManager.getApplicationInfo(application.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                isAppInstalled = false;
            }
            Timber.i("isAppInstalled: " + isAppInstalled);
            if (isAppInstalled) {
                viewHolder.buttonInstall.setVisibility(View.GONE);
            }

            viewHolder.buttonDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Timber.i("buttonDownload onClick");

                    Timber.i("Downloading " + application.getPackageName() + " (version " + applicationVersion.getVersionCode() + ")...");

                    viewHolder.buttonDownload.setVisibility(View.GONE);
                    viewHolder.progressBarDownloadProgress.setVisibility(View.VISIBLE);
                    viewHolder.textViewDownloadProgress.setVisibility(View.VISIBLE);

                    // Initiate download of the latest APK version
                    Timber.i("applicationVersion: " + applicationVersion);
                    new DownloadApplicationAsyncTask(
                            viewHolder.progressBarDownloadProgress,
                            viewHolder.textViewDownloadProgress,
                            viewHolder.buttonInstall
                    ).execute(applicationVersion);
                }
            });

            viewHolder.buttonInstall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Timber.i("buttonInstall onClick");

                    // Initiate installation of the latest APK version
                    Timber.i("Installing " + applicationVersion.getApplication().getPackageName() + " (version " + applicationVersion.getVersionCode() + ")...");

                    String fileName = applicationVersion.getApplication().getPackageName() + "-" + applicationVersion.getVersionCode() + ".apk";
                    Timber.i("fileName: " + fileName);

                    String language = UserPrefsHelper.getLocale(context).getLanguage();
                    File apkDirectory = new File(Environment.getExternalStorageDirectory() + "/.elimu-ai/appstore/apks/" + language);

                    File apkFile = new File(apkDirectory, fileName);
                    Timber.i("apkFile: " + apkFile);

                    // Install APK file
                    // TODO: Check for root access. If root access, install APK without prompting for user confirmation.
                    if (Build.VERSION.SDK_INT >= 24) {
                        // See https://developer.android.com/guide/topics/permissions/requesting.html#install-unknown-apps
                        Uri apkUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", apkFile);
                        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        intent.setData(apkUri);
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(intent);
                    } else {
                        Uri apkUri = Uri.fromFile(apkFile);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                }
            });
        }

        return listItem;
    }


    /**
     * Downloads APK file and updates progress bar in the UI during the download.
     */
    private class DownloadApplicationAsyncTask extends AsyncTask<ApplicationVersion, Integer, Integer> {

        private ApplicationVersion applicationVersion;

        private ProgressBar progressBarDownloadProgress;
        private TextView textViewDownloadProgress;
        private Button buttonInstall;

        public DownloadApplicationAsyncTask(ProgressBar progressBarDownloadProgress, TextView textViewDownloadProgress, Button buttonInstall) {
            this.progressBarDownloadProgress = progressBarDownloadProgress;
            this.textViewDownloadProgress = textViewDownloadProgress;
            this.buttonInstall = buttonInstall;
        }

        @Override
        protected Integer doInBackground(ApplicationVersion... applicationVersions) {
            Timber.i("doInBackground");

            applicationVersion = applicationVersions[0];
            Timber.i("applicationVersion.getApplication(): " + applicationVersion.getApplication());
            Timber.i("applicationVersion.getFileSizeInKb(): " + applicationVersion.getFileSizeInKb());
            Timber.i("applicationVersion.getFileUrl(): " + applicationVersion.getFileUrl());
            Timber.i("applicationVersion.getContentType(): " + applicationVersion.getContentType());
            Timber.i("applicationVersion.getVersionCode(): " + applicationVersion.getVersionCode());
            Timber.i("applicationVersion.getStartCommand(): " + applicationVersion.getStartCommand());
            Timber.i("applicationVersion.getTimeUploaded().getTime(): " + applicationVersion.getTimeUploaded().getTime());

            // Reset to initial state
            Integer fileSizeInKbsDownloaded = 0;
            publishProgress(fileSizeInKbsDownloaded);

            // Download APK file and store it on SD card
            String fileUrl = BuildConfig.BASE_URL + applicationVersion.getFileUrl() +
                    "?deviceId=" + DeviceInfoHelper.getDeviceId(context) +
                    "&checksum=" + ChecksumHelper.getChecksum(context) +
                    "&locale=" + UserPrefsHelper.getLocale(context) +
                    "&deviceModel=" + DeviceInfoHelper.getDeviceModel(context) +
                    "&osVersion=" + Build.VERSION.SDK_INT +
                    "&applicationId=" + DeviceInfoHelper.getApplicationId(context) +
                    "&appVersionCode=" + DeviceInfoHelper.getAppVersionCode(context);
            Timber.i("fileUrl: " + fileUrl);

            String fileName = applicationVersion.getApplication().getPackageName() + "-" + applicationVersion.getVersionCode() + ".apk";
            Timber.i("fileName: " + fileName);

            Timber.i("Downloading APK: " + applicationVersion.getApplication().getPackageName() + " (version " + applicationVersion.getVersionCode() + ", " + applicationVersion.getFileSizeInKb() + "kB)");

//            File apkFile = ApkLoader.loadApk(fileUrl, fileName, context);
            // Copied from ApkLoader#loadApk:
            String urlValue = fileUrl;
            Timber.i("Downloading from " + urlValue + "...");

            String language = UserPrefsHelper.getLocale(context).getLanguage();
            File apkDirectory = new File(Environment.getExternalStorageDirectory() + "/.elimu-ai/appstore/apks/" + language);
            Timber.i("apkDirectory: " + apkDirectory);
            if (!apkDirectory.exists()) {
                apkDirectory.mkdirs();
            }

            File apkFile = new File(apkDirectory, fileName);
            Timber.i("apkFile: " + apkFile);
            Timber.i("apkFile.exists(): " + apkFile.exists());

            if (!apkFile.exists()) {
                FileOutputStream fileOutputStream = null;
                try {
                    URL url = new URL(urlValue);

                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    httpURLConnection.connect();

                    int responseCode = httpURLConnection.getResponseCode();
                    Timber.i("responseCode: " + responseCode);
                    InputStream inputStream = null;
                    if (responseCode == 200) {
                        inputStream = httpURLConnection.getInputStream();
                    } else {
                        inputStream = httpURLConnection.getErrorStream();
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                        String errorResponse = "";
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            errorResponse += line;
                        }
                        Timber.w("errorResponse: " + errorResponse);
                        return null;
                    }

//                    byte[] bytes = IOUtils.toByteArray(inputStream);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead);

                        fileSizeInKbsDownloaded += (bytesRead / 1024);
                        publishProgress(fileSizeInKbsDownloaded);
                    }
                    byte[] bytes = byteArrayOutputStream.toByteArray();

                    fileOutputStream = new FileOutputStream(apkFile);
                    fileOutputStream.write(bytes);
                    fileOutputStream.flush();
                } catch (MalformedURLException e) {
                    Timber.e(e, "MalformedURLException");
                } catch (IOException e) {
                    Timber.e(e, "IOException");
                } finally {
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            Timber.i(e, "IOException");
                        }
                    }
                }
            }

            Timber.i("apkFile: " + apkFile);

            return fileSizeInKbsDownloaded;
        }

        @Override
        protected void onProgressUpdate(Integer... fileSizeInKbsDownloadeds) {
            Timber.d("onProgressUpdate");
            super.onProgressUpdate(fileSizeInKbsDownloadeds);

            int fileSizeInKbsDownloaded = fileSizeInKbsDownloadeds[0];
            Timber.d("fileSizeInKbsDownloaded: " + fileSizeInKbsDownloaded);

            int progress = (fileSizeInKbsDownloaded * 100) / applicationVersion.getFileSizeInKb();
            Timber.d("progress: " + progress);
            progressBarDownloadProgress.setProgress(progress);

            // E.g. "6.00 MB/12.00 MB   50%"
            String progressText = (fileSizeInKbsDownloaded / 1024) + " MB/" + (applicationVersion.getFileSizeInKb() / 1024) + " MB   " + progress + "%";
            Timber.d("progressText: " + progressText);
            textViewDownloadProgress.setText(progressText);
        }

        @Override
        protected void onPostExecute(Integer fileSizeInKbsDownloaded) {
            Timber.i("onPostExecute");
            super.onPostExecute(fileSizeInKbsDownloaded);

            // Hide progress indicators
            progressBarDownloadProgress.setVisibility(View.GONE);
            textViewDownloadProgress.setVisibility(View.GONE);
            buttonInstall.setVisibility(View.VISIBLE);

            Timber.i("fileSizeInKbsDownloaded: " + fileSizeInKbsDownloaded);
        }
    }
}
