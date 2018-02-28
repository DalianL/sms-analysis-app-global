package unice.com.smsanalysis;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.microsoft.windowsazure.mobileservices.*;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.TableOperationCallback;

public class MainActivity extends Activity {
    TextView textView;
    int anHour = 60000*60;
    private JobScheduler mJobScheduler;
    private Button mGoWebsiteButton;
    private Button mCancelAllJobsButton;

    /**
    *   Private class HttpAsyncTask to do network things in background
    *   and set the content of the view.
    */
    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            return testRestHttp(urls);
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getBaseContext(), "Data received!", Toast.LENGTH_LONG).show();
            textView.setText(textView.getText() + "\n\nRestHttp tests :\n" + result);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.SMS);
        mJobScheduler = (JobScheduler) getSystemService( Context.JOB_SCHEDULER_SERVICE );
        mGoWebsiteButton = (Button) findViewById( R.id.goWebsite );
        mCancelAllJobsButton = (Button) findViewById( R.id.stopService );

        // call AsynTask to perform network operation on separate thread
        //new HttpAsyncTask().execute("https://www.e-meta.fr/testjson.php", "https://www.e-meta.fr/test.json");
        //getSMSDetails();

        // New builder for the service
        JobInfo.Builder builder = new JobInfo.Builder(1, new ComponentName(getPackageName(), JobSchedulerService.class.getName()));
        // Doit être connecté au réseau
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        // Only with charging.
        builder.setRequiresCharging(true);
        // Each hour.
        builder.setPeriodic(anHour);
        // Stay after reboot
        builder.setPersisted(true);

        // Run the job
        if( mJobScheduler.schedule( builder.build() ) <= 0 ) {
            //If something goes wrong
            Log.d("error job", "job goes wrong");
        }

        textView.setText("SMS ANALYSIS APP");
        textView.setTextColor(Color.RED);

        // Click to stop the job service.
        mCancelAllJobsButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                mJobScheduler.cancelAll();
                Toast.makeText(getApplicationContext(), "Service stopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
    // Get details from SMS
    private void getSMSDetails() {
        long startTime = System.currentTimeMillis();
        // Sms HashTable
        Sms sms = new Sms();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("Sms Analysis and creating matrix :");
        Uri uri = Uri.parse("content://sms");
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        // Move with cursor
        if (cursor.moveToFirst()) {
            // cursor.getCount();
            for (int i = 0; i < numberSmsToRead; i++) {
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body")).toString();
                int nbrCaracters = body.length();
                String number = cursor.getString(cursor.getColumnIndexOrThrow("address")).toString();
                String name = UserManagement.getContactName(this,number).toString();
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date")).toString();
                Date smsDayTime = new Date(Long.valueOf(date));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type")).toString();
                int dayWeek = DateControl.dayOfWeek(DateControl.getDayOfWeek(smsDayTime));
                int dayWeekend = DateControl.dayOfWeekend(DateControl.getDayOfWeek(smsDayTime));
                String typeOfSMS = null;
                // Calculate hour of day using calendar instance
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(date));
                int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
                // Switch for type of SMS
                switch (Integer.parseInt(type)) {
                    case 1:
                        typeOfSMS = "INBOX";
                        break;
                    case 2:
                        typeOfSMS = "SENT";
                        break;
                    case 3:
                        typeOfSMS = "DRAFT";
                        break;
                }
                //stringBuffer.append("\nPhone Number:--- " + number +" \nMessage Type:--- " + typeOfSMS +" \nMessage Date:--- " + smsDayTime);
                // stringBuffer.append("\n----------------------------------");
                cursor.moveToNext();
                // Add to matrix
                ArrayList<String> contenu = new ArrayList<String>();
                contenu.add(name);
                contenu.add(number);
                // Date in UNIX FORMAT (milliseconds)
                contenu.add(date);
                // Type of SMS
                contenu.add(typeOfSMS);
                // PART DAY/HOUR IN A NICHE
                // if day of week
                contenu.add(Integer.toString(dayWeek));
                // if day of weekend
                contenu.add(Integer.toString(dayWeekend));
                // nbr caracters
                contenu.add(Integer.toString(nbrCaracters));
                // hour 0h - 5h
                contenu.add(Integer.toString(DateControl.isNight(hourOfDay)));
                // hour 6h - 10h
                contenu.add(Integer.toString(DateControl.isMorning(hourOfDay)));
                // hour 11h - 14h
                contenu.add(Integer.toString(DateControl.isMidday(hourOfDay)));
                // hour 15h - 18h
                contenu.add(Integer.toString(DateControl.isAfternoon(hourOfDay)));
                // hour 19h - 23h
                contenu.add(Integer.toString(DateControl.isEvening(hourOfDay)));
                // Add sms to user in hashmap sms
                sms.addSmsToUser(name, Long.parseLong(date), nbrCaracters);
                // PART FILTER AND CACULATE AVERAGE AND NUMBER OF SMS WITH A DISTANCE OF TIME
                // Filter (1 minute), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+oneMinute,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (2 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+twoMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (5 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fiveMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (10 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+tenMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (20 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+twentyMinutes,Long.parseLong(date), name);  contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (30 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+thirtyMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (40 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fourtyMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (50 minutes), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fiftyMinutes,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (1 hour), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+oneHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (1 hour and a half), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+oneHourAndHalf,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (2 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+twoHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (3 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+threeHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (4 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fourHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (5 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fiveHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (6 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+sixHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (12 hours), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+twelveHour,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (1 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+oneDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (2 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+twoDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (3 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+threeDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (4 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fourDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (5 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+fiveDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (6 day), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+sixDay,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Filter (1 week), calculate the average of words and the number of messages.
                sms.filterSmsByTime(Long.parseLong(date)+oneWeek,Long.parseLong(date), name); contenu.add(sms.getCountSms()+""); contenu.add(sms.getAverageWords()+"");
                // Add sms vector to matrix
                matrice.put(i,contenu);
            }
            stringBuffer.append("\n Affichage de la matrice :\n" + matrice.toString());
            textView.setText(stringBuffer);
            postContent = matrice.toString();
            Log.i("Matrice", matrice.toString());
            long endTime   = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            Log.d("total time :", totalTime+"");
        }
        cursor.close();
    }
     **/

    // Test our class Rest
    public String testRestHttp(String... urls) {
        // url[0] = url to test send
        // url[1] = url to test receive
        String urlSend = urls[0];
        String urlReceive = urls[1];
        // REST HTTP SENDER TO TEST SENDING DATA
        RestHttp sender = new RestHttp(urlSend);
        int result = sender.sendPostData("ok");
        // REST HTTP RECEIVER TO TEST RECEIVED DATA
        RestHttp receiver = new RestHttp(urlReceive);
        JsonReader jsonReader = receiver.getData();
        // If the jsonReader is not null
        if(jsonReader != null) {
            try {
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals("test")) {
                        String text = jsonReader.nextString();
                        return text;
                    } else {
                        jsonReader.skipValue();
                    }
                }
                jsonReader.endObject();

            } catch (java.io.IOException e) {
                Log.e("error", "io error");
            }
        } else {
            Log.e("ResultReceive", "error");
        }
        return "false";
    }
}