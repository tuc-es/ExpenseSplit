package de.safeml.expensesplit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TableLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {


    // Helper functions
    String doPostRequest(String base, Map<String,Object> params) throws IOException {

        // Based on: https://stackoverflow.com/questions/4205980/java-sending-http-parameters-via-post-method-easily

        try {
            URL url = new URL(base);

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);

            Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

            StringBuilder b = new StringBuilder();
            for (int c; (c = in.read()) >= 0; )
                b.append((char) c);
            return b.toString();
        } catch (ProtocolException p) {
            throw new RuntimeException(p); // Make fatal
        }
    }



    // Data in Memory
    final ArrayList<String> newTeamMembers = new ArrayList<String>();
    ArrayAdapter<String> newTeamMemberAdapter;
    final ArrayList<String> teamsAvailable = new ArrayList<String>();
    ArrayAdapter<String> teamsAvailableAdapter;
    final ArrayList<String> membersAvailableForReimbursement = new ArrayList<String>();
    ArrayAdapter<String> membersAvailableForReimbursementAdapter;
    int selectedItem_spinnerReimbursementPerson = 0;

    String currentAccount;

    // Stored data
    SharedPreferences sharedpreferences;
    Lock sharedPreferencesLock;



    // Custom actions
    void addExpense() {

        EditText et = findViewById(R.id.editTextNewExpenseText);
        String text = et.getText().toString().trim();
        if (text.length()==0) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("The expense text cannot be empty");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        EditText et2 = findViewById(R.id.editTextNewExpenseValue);
        String value = et2.getText().toString().trim();
        double valueDouble;

        try {
            valueDouble = Double.parseDouble(value);


        } catch (NumberFormatException err) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("The expense value is not valid");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        if (valueDouble<=0.01) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("The expense value needs to be strictly positive (and at least 0.01)");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        int intValue = (new Double(valueDouble * 100)).intValue();

        String newEvent = "E"+Integer.toString(intValue)+" "+text.replace("\n"," ");

        // Store new event
        sharedPreferencesLock.lock();

        try {
            SharedPreferences.Editor editor = sharedpreferences.edit();

            if (sharedpreferences.contains("tasks")) {
                String res = sharedpreferences.getString("tasks", null);

                editor.putString("tasks", res+"\n"+newEvent);
                editor.commit();
            } else {
                editor.putString("tasks", newEvent);
                editor.commit();
            }


        } finally {
            sharedPreferencesLock.unlock();
        }

        updateUnstoredDataView();
        emit_actionToBeExecutedWasStored();
        et.setText("");
        et2.setText("");

    }


    void transferMoney() {


        EditText et2 = findViewById(R.id.editTextNewReimbursementValue);
        String value = et2.getText().toString().trim();
        double valueDouble;

        try {
            valueDouble = Double.parseDouble(value);


        } catch (NumberFormatException err) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("The reimbursement value is not valid");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        if (valueDouble<=0.01) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("The reimbursement value needs to be strictly positive (and at least 0.01)");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        int intValue = (new Double(valueDouble * 100)).intValue();

        int toWhom = selectedItem_spinnerReimbursementPerson;
        if (toWhom==-1) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error");
            alertDialog.setMessage("No person to reimburse to has been selected.");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return;
        }

        String newEvent = "P"+Integer.toString(intValue)+" "+Integer.toString(toWhom);

        // Store new event
        sharedPreferencesLock.lock();

        try {
            SharedPreferences.Editor editor = sharedpreferences.edit();

            if (sharedpreferences.contains("tasks")) {
                String res = sharedpreferences.getString("tasks", null);

                editor.putString("tasks", res+"\n"+newEvent);
                editor.commit();
            } else {
                editor.putString("tasks", newEvent);
                editor.commit();
            }


        } finally {
            sharedPreferencesLock.unlock();
        }

        updateUnstoredDataView();
        emit_actionToBeExecutedWasStored();
        et2.setText("");

    }


    void deactivateMenuItemsOtherThanSelectAccountAndNewAccount() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu menu=navigationView.getMenu();
        for (int b : new int[] { R.id.MenuItemExpenseList,
                R.id.MenuItemTrackReimbursement,
                R.id.MenuItemTeamAdministration,
                R.id.MenuItemOverview,
                R.id.MenuItemOfflineActions,
                R.id.MenuItemNonApprovedExpenses,
                R.id.MenuItemAddExpense
        }) {
            MenuItem a = menu.findItem(b);
            a.setEnabled(false);
        }
    }

    void activateMenuItemsOtherThanSelectAccountAndNewAccount() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu menu=navigationView.getMenu();
        for (int b : new int[] { R.id.MenuItemExpenseList,
                R.id.MenuItemTrackReimbursement,
                R.id.MenuItemTeamAdministration,
                R.id.MenuItemOverview,
                R.id.MenuItemOfflineActions,
                R.id.MenuItemNonApprovedExpenses,
                R.id.MenuItemAddExpense
        }) {
            MenuItem a = menu.findItem(b);
            a.setEnabled(true);
        }
    }

    void addMemberToTeam() {
        EditText et = findViewById(R.id.editTextNewMemberName);
        Log.w("MSG","Adding member: "+et.getText());
        newTeamMemberAdapter.add(et.getText().toString());
        et.setText("");
    }

    void addTeamToTeamList() {
        Log.w("MSG","AddTeamToTeamList");
        // Adds the team information from the edit box to the list of teams
        EditText et = findViewById(R.id.editTextNewTeamLogin);
        String text = et.getText().toString().trim();
        if (text.length()!=12) {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Failed");
            alertDialog.setMessage("A team member login code always has exactly 12 characters");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        } else {
            Log.w("MSG", "Adding team: " + et.getText());
            teamsAvailableAdapter.add(et.getText().toString());
            et.setText("");
            emit_newTeamIdentifierIsFine();
        }
    }

    String unstoredDataError = null;

    void updateUnstoredDataView() {
        // Can run from working thread.
        sharedPreferencesLock.lock();
        String tasks = "";
        try {
            tasks = sharedpreferences.getString("tasks", "");
        } finally {
            sharedPreferencesLock.unlock();
        }

        StringBuilder listOfTasks = new StringBuilder();

        if (tasks == "") {
            listOfTasks.append("Everything is up-to-date!");
        } else {
            String[] tasksList = tasks.split("\n");
            listOfTasks.append("<html><h1>Tasks to be performed:</h1><UL>");
            for (String a : tasksList) {
                if (a.length()>0) {
                    if (a.charAt(0)=='A') {
                        listOfTasks.append("<LI>Approve expense/payment ");
                        listOfTasks.append(a.substring(1)+"</LI>");
                    } else if (a.charAt(0)=='D') {
                        listOfTasks.append("<LI>Reject expense/payment ");
                        listOfTasks.append(a.substring(1)+"</LI>");
                    } else if (a.charAt(0)=='E') {
                        listOfTasks.append("<LI>New expense of value (in cents) ");
                        listOfTasks.append(a.substring(1,a.indexOf(" "))+"</LI>");
                    }
                    else  {
                        listOfTasks.append("<LI>Unknown type</LI>");
                    }
                } else {
                    listOfTasks.append("<LI>Empty task!</LI>");
                }
            }
            listOfTasks.append("</UL>");
            String a = unstoredDataError;
            if(a!=null) {
                listOfTasks.append("<B>Last Error:</B> " + a);
            }
            listOfTasks.append("</html>");
        }

        final String html = listOfTasks.toString();

        runOnUiThread(new java.lang.Thread() {
            public void run() {
                WebView wva = findViewById(R.id.webViewOfflineActions);
                wva.loadData(html, "text/html; charset=utf-8", "utf-8");
            }
        });

    }

    // ========================
    // Custom threads
    // ========================

    void storeData() {

        // Get parameters that should not change while
        // this thread is running.
        sharedPreferencesLock.lock();
        String threadAccount;

        try {
            threadAccount = currentAccount;
        } finally {
            sharedPreferencesLock.unlock();
        }


        // Repeat until there are o more tasks.
        while (true) {

            // Can run from working thread.
            sharedPreferencesLock.lock();
            String tasks = "";
            try {
                tasks = sharedpreferences.getString("tasks", "");
                if (tasks == "") {
                    return; // Nothing more to do!
                }
            } finally {
                sharedPreferencesLock.unlock();
            }

            StringBuilder listOfTasks = new StringBuilder();
            String[] tasksList = tasks.split("\n");
            String taskExecuted = tasksList[0];

            // Build HTTPS request
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("id", threadAccount);
            params.put("action", taskExecuted);

            try {
                final String result = doPostRequest("http://expensesplit.safeml.de/cgi-bin/action.py", params).trim();

                if ((result.startsWith("Error:")) || !(result.trim().equals("OK"))) {
                    runOnUiThread(new java.lang.Thread() {
                        public void run() {
                            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                            alertDialog.setTitle("Failed");
                            alertDialog.setMessage(result);
                            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }
                    });
                    return;
                }


                // Result is fine! Remove task
                sharedPreferencesLock.lock();
                tasks = "";
                try {
                    tasks = sharedpreferences.getString("tasks", "");
                    tasksList = tasks.split("\n");
                    Vector<String> remainingTasks = new Vector<String>();
                    for (String s : tasksList) {
                        if (s.equals(taskExecuted)) {
                            // Nothing to do.
                        } else {
                            remainingTasks.add(s);
                        }
                    }
                    tasks = TextUtils.join("\n",remainingTasks);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putString("tasks", tasks);
                    editor.commit();
                } finally {
                    sharedPreferencesLock.unlock();
                }


            } catch (IOException e) {
                unstoredDataError = e.getMessage()+"<BR/>"+e.getLocalizedMessage()+"<BR/>"+e.getStackTrace();
                updateUnstoredDataView();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e2) {
                    // Does not matter. Can just run again.
                }
            }
        }
    }


    void registerNewTeam() {

        Log.w("MSG","RegisterNewTeam Called!");

        Map<String,Object> params = new LinkedHashMap<>();

        StringBuilder b = new StringBuilder();
        for (String a : newTeamMembers) {
            b.append(a);
            b.append("\n");
        }

        params.put("names", b.toString());

        try {
            final String result = doPostRequest("http://expensesplit.safeml.de/cgi-bin/newledger.py",params).trim();

            if (result.startsWith("Error:")) {
                runOnUiThread(new java.lang.Thread() { public void run() {
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Failed");
                    alertDialog.setMessage(result);
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }});

            } else {

                // Ok, we got a new one!
                String[] parts = result.split("\n");
                if (( parts.length!=3) || (!parts[0].trim().equals("OK"))) {
                    runOnUiThread(new java.lang.Thread() {
                        public void run() {
                            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                            alertDialog.setTitle("Failed");
                            alertDialog.setMessage("Did not understand server return data.");
                            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }
                    });

                } else {


                    // Ok, we can clear
                    Log.w("HTTPRES",result);
                    // Log.w("HTTPRES",String.format("%040x", new BigInteger(1, result.getBytes("utf-8"))));

                    final String newAccount = parts[1]+parts[2];

                    runOnUiThread(new java.lang.Thread() { public void run() {
                        newTeamMemberAdapter.clear();

                        sharedPreferencesLock.lock();
                        try {
                            SharedPreferences.Editor editor = sharedpreferences.edit();

                            if (sharedpreferences.contains("teams")) {
                                String res = sharedpreferences.getString("teams", null);

                                // TODO: Swap the following two lines
                                //editor.putString("teams", newAccount+"-"+res);
                                editor.putString("teams", newAccount);

                                editor.commit();
                            } else {
                                editor.putString("teams", newAccount);
                                editor.commit();
                            }
                        } finally {
                            sharedPreferencesLock.unlock();
                        }

                    }});

                    // Team loaded!
                    emit_MenuItemSelectAccount();
                }
            }

        } catch (IOException e) {
            // failed!

            runOnUiThread(new java.lang.Thread() { public void run() {

                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Failed");
                alertDialog.setMessage("I/O Error - Perhaps your internet connection is missing");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }});
        }

    }


    void updateAvailableTeamsList() {
        sharedPreferencesLock.lock();
        try {

            Log.w("MSG", "updateAvailableTeamsList");
            String res = sharedpreferences.getString("teams", null);
            if (res == null) {
                teamsAvailableAdapter.clear();
            } else {
                String[] parts = res.split("-");
                Log.w("MSG", "updateAvailableTeamsListRes");
                for (String s : parts) {
                    Log.w("MSGTeam", s);
                    teamsAvailableAdapter.add(s);
                }

            }
        } finally {
            sharedPreferencesLock.unlock();
        }
    }

    void loadTeamFailureShowMessage(String message) {
        final String msg = message;
        runOnUiThread(new java.lang.Thread() {
            public void run() {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Failed");
                alertDialog.setMessage(msg);
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
                emit_loadTeamFail();
            }
        });
    }

    void loadTeam() {

        // Which team?
        currentAccount = teamsAvailable.get(selectedItem_listViewAvailableTeams);
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("id", currentAccount);

        try {
            final String result = doPostRequest("http://expensesplit.safeml.de/cgi-bin/loadLedger.py", params).trim();

            if (result.startsWith("Error:")) {
                runOnUiThread(new java.lang.Thread() {
                    public void run() {
                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setTitle("Failed");
                        alertDialog.setMessage(result);
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        alertDialog.show();
                        emit_loadTeamFail();
                    }
                });
            } else {

                // Split up result into parts
                String[] resultParts = result.split("\n");
                for (int i = 0;i<resultParts.length;i++) resultParts[i] = resultParts[i].trim();
                StringBuilder resultOverview = new StringBuilder();
                int posResult = 0;
                if (!(resultParts[posResult].equals("<Overview>"))) {
                    Log.w("MSGO",resultParts[posResult]);
                    Log.w("MSGO",Integer.toString(posResult));
                    loadTeamFailureShowMessage("Did not understand the server. Perhaps you have to log into the current network?");
                    return;
                }

                posResult++;
                while (!(resultParts[posResult].equals("</Overview>"))) {
                    resultOverview.append("\n");
                    resultOverview.append(resultParts[posResult]);
                    Log.w("MSG","!EndOverview");
                    Log.w("MSG",resultParts[posResult]);
                    posResult++;
                    if (posResult>=resultParts.length) {
                        loadTeamFailureShowMessage("Overview End not found");
                        return;
                    }
                }

                // Update overview
                final WebView wv = findViewById(R.id.webViewOverview);
                final String overViewString = resultOverview.toString();
                runOnUiThread(new java.lang.Thread() {
                    public void run() {
                        wv.loadData(overViewString, "text/html; charset=utf-8", "utf-8");
                    }
                });

                // Update expense list
                posResult++;
                if (!(resultParts[posResult].equals("<Expenses>"))) {
                    loadTeamFailureShowMessage("Expenses not found");
                    return;
                }
                StringBuilder resultExpenses = new StringBuilder();
                while (!(resultParts[posResult].equals("</Expenses>"))) {
                    resultExpenses.append("\n");
                    resultExpenses.append(resultParts[posResult]);
                    Log.w("MSG","!EndExpenses");
                    Log.w("MSG",resultParts[posResult]);
                    posResult++;
                    if (posResult>=resultParts.length) {
                        loadTeamFailureShowMessage("Expense End not found");
                        return;
                    }
                }

                final WebView wve = findViewById(R.id.webViewExpenseList);
                final String expenseString = resultExpenses.toString();
                runOnUiThread(new java.lang.Thread() {
                    public void run() {
                        wve.loadData(expenseString, "text/html; charset=utf-8", "utf-8");
                    }
                });



                // Next: Expenses to be approved!
                final WebView wva = findViewById(R.id.webViewExpensesToApprove);

                posResult++;
                if (!(resultParts[posResult].equals("<Approve>"))) {
                    loadTeamFailureShowMessage("Approve not found");
                    return;
                }
                StringBuilder resultApprove = new StringBuilder();
                while (!(resultParts[posResult].equals("</Approve>"))) {
                    resultApprove.append("\n");
                    resultApprove.append(resultParts[posResult]);
                    Log.w("MSG","!Approve");
                    Log.w("MSG",resultParts[posResult]);
                    posResult++;
                    if (posResult>=resultParts.length) {
                        loadTeamFailureShowMessage("Approve End not found");
                        return;
                    }
                }

                final String approveString = resultApprove.toString();
                runOnUiThread(new java.lang.Thread() {
                    public void run() {

                        WebSettings ws = wva.getSettings();
                        ws.setJavaScriptEnabled(true);
                        wva.addJavascriptInterface(new Object()
                        {
                            @JavascriptInterface
                            public void approve(String id)
                            {
                                // Deal with a click on the OK button

                                StringBuilder approveAction = new StringBuilder();
                                approveAction.append("A");
                                approveAction.append(id);

                                Log.w("MSG","Approve called!");

                                sharedPreferencesLock.lock();

                                try {
                                    SharedPreferences.Editor editor = sharedpreferences.edit();

                                    if (sharedpreferences.contains("tasks")) {
                                        String res = sharedpreferences.getString("tasks", null);

                                        editor.putString("tasks", res+"\n"+approveAction.toString());
                                        editor.commit();
                                    } else {
                                        editor.putString("tasks", approveAction.toString());
                                        editor.commit();
                                    }


                                } finally {
                                    sharedPreferencesLock.unlock();
                                }

                                updateUnstoredDataView();
                                emit_actionToBeExecutedWasStored();
                            }

                            @JavascriptInterface
                            public void disapprove(String id)
                            {
                                // Deal with a click on the OK button

                                StringBuilder approveAction = new StringBuilder();
                                approveAction.append("D");
                                approveAction.append(id);

                                Log.w("MSG","Disapprove called!");

                                sharedPreferencesLock.lock();

                                try {
                                    SharedPreferences.Editor editor = sharedpreferences.edit();

                                    if (sharedpreferences.contains("tasks")) {
                                        String res = sharedpreferences.getString("tasks", null);

                                        editor.putString("tasks", res+"\n"+approveAction.toString());
                                        editor.commit();
                                    } else {
                                        editor.putString("tasks", approveAction.toString());
                                        editor.commit();
                                    }


                                } finally {
                                    sharedPreferencesLock.unlock();
                                }

                                updateUnstoredDataView();
                                emit_actionToBeExecutedWasStored();
                            }
                        }, "approvalinterface");

                        wva.loadData(approveString, "text/html; charset=utf-8", "utf-8");
                    }
                });


                // Next: Expenses to be approved!
                final WebView wvm = findViewById(R.id.webViewAdmin);

                posResult++;
                if (!(resultParts[posResult].equals("<Admin>"))) {
                    loadTeamFailureShowMessage("Approve not found");
                    return;
                }
                StringBuilder resultAdmin = new StringBuilder();
                while (!(resultParts[posResult].equals("</Admin>"))) {
                    resultAdmin.append("\n");
                    resultAdmin.append(resultParts[posResult]);
                    posResult++;
                    if (posResult>=resultParts.length) {
                        loadTeamFailureShowMessage("Admin End not found");
                        return;
                    }
                }

                posResult++;
                if (!(resultParts[posResult].equals("<Members>"))) {
                    loadTeamFailureShowMessage("Members not found");
                    return;
                }
                posResult++;
                final Vector<String> newMembers = new Vector<String>();
                while (!(resultParts[posResult].equals(""))) {
                    newMembers.add(resultParts[posResult]);
                    posResult++;
                    if (posResult>=resultParts.length) {
                        loadTeamFailureShowMessage("Members End not found");
                        return;
                    }
                }

                final String adminString = resultAdmin.toString();
                runOnUiThread(new java.lang.Thread() {
                    public void run() {

                        WebSettings ws = wvm.getSettings();
                        ws.setJavaScriptEnabled(true);
                        wvm.addJavascriptInterface(new Object() {
                            @JavascriptInterface
                            public void approve(String id) {
                                // Deal with a click on the OK button

                                StringBuilder approveAction = new StringBuilder();
                                approveAction.append("M");
                                approveAction.append(id);

                                Log.w("MSG", "Approve called!");

                                sharedPreferencesLock.lock();

                                try {
                                    SharedPreferences.Editor editor = sharedpreferences.edit();

                                    if (sharedpreferences.contains("tasks")) {
                                        String res = sharedpreferences.getString("tasks", null);

                                        editor.putString("tasks", res + "\n" + approveAction.toString());
                                        editor.commit();
                                    } else {
                                        editor.putString("tasks", approveAction.toString());
                                        editor.commit();
                                    }


                                } finally {
                                    sharedPreferencesLock.unlock();
                                }

                                updateUnstoredDataView();
                                emit_actionToBeExecutedWasStored();
                            }

                            @JavascriptInterface
                            public void disapprove(String id) {
                                // Deal with a click on the OK button

                                StringBuilder approveAction = new StringBuilder();
                                approveAction.append("R");
                                approveAction.append(id);

                                Log.w("MSG", "Disapprove called!");

                                sharedPreferencesLock.lock();

                                try {
                                    SharedPreferences.Editor editor = sharedpreferences.edit();

                                    if (sharedpreferences.contains("tasks")) {
                                        String res = sharedpreferences.getString("tasks", null);

                                        editor.putString("tasks", res + "\n" + approveAction.toString());
                                        editor.commit();
                                    } else {
                                        editor.putString("tasks", approveAction.toString());
                                        editor.commit();
                                    }

                                    updateUnstoredDataView();
                                    emit_actionToBeExecutedWasStored();


                                } finally {
                                    sharedPreferencesLock.unlock();
                                }

                            }
                        }, "approvalinterface");

                        wvm.loadData(adminString, "text/html; charset=utf-8", "utf-8");
                        membersAvailableForReimbursementAdapter.clear();
                        membersAvailableForReimbursementAdapter.addAll(newMembers);
                        emit_loadTeamSucceed();
                    }
                });


            }

        } catch (IOException ioe) {
            final String msg = ioe.getMessage();
            runOnUiThread(new java.lang.Thread() {
                public void run() {
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("IO Failure");
                    alertDialog.setMessage(msg);
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                    emit_loadTeamFail();
                }
            });

        }

    }

    // --SYNTHESIZED-CODE-SUBCLASSES-START--
    void gameAction0() {
      logCurrentState("GameAction0",currentSystemGoal,controllerState);
    }
    void gameAction1() {
      logCurrentState("GameAction1",currentSystemGoal,controllerState);
    }
    void gameAction2() {
      logCurrentState("GameAction2",currentSystemGoal,controllerState);
    }
    void gameAction3() {
      logCurrentState("GameAction3",currentSystemGoal,controllerState);
    }
    void gameAction4() {
      logCurrentState("GameAction4",currentSystemGoal,controllerState);
    }
    void gameAction5() {
      logCurrentState("GameAction5",currentSystemGoal,controllerState);
    }
    void gameAction6() {
      logCurrentState("GameAction6",currentSystemGoal,controllerState);
    }
    void gameAction7() {
      logCurrentState("GameAction7",currentSystemGoal,controllerState);
    }
    void gameAction8() {
      logCurrentState("GameAction8",currentSystemGoal,controllerState);
    }
    void gameAction9() {
      logCurrentState("GameAction9",currentSystemGoal,controllerState);
    }
    void gameAction10() {
      logCurrentState("GameAction10",currentSystemGoal,controllerState);
    }
    void gameAction11() {
      logCurrentState("GameAction11",currentSystemGoal,controllerState);
    }
    void gameAction12() {
      logCurrentState("GameAction12",currentSystemGoal,controllerState);
    }
    void gameAction13() {
      logCurrentState("GameAction13",currentSystemGoal,controllerState);
    }
    void gameAction14() {
      logCurrentState("GameAction14",currentSystemGoal,controllerState);
    }
    void gameAction15() {
      logCurrentState("GameAction15",currentSystemGoal,controllerState);
    }
    void gameAction16() {
      logCurrentState("GameAction16",currentSystemGoal,controllerState);
    }
    void gameAction17() {
      logCurrentState("GameAction17",currentSystemGoal,controllerState);
    }
    void gameAction18() {
      logCurrentState("GameAction18",currentSystemGoal,controllerState);
    }
    void gameAction19() {
      logCurrentState("GameAction19",currentSystemGoal,controllerState);
    }
    void gameAction20() {
      logCurrentState("GameAction20",currentSystemGoal,controllerState);
    }
    void gameAction21() {
      logCurrentState("GameAction21",currentSystemGoal,controllerState);
    }
    void gameAction22() {
      logCurrentState("GameAction22",currentSystemGoal,controllerState);
    }
    void gameAction23() {
      logCurrentState("GameAction23",currentSystemGoal,controllerState);
    }
    void gameAction24() {
      logCurrentState("GameAction24",currentSystemGoal,controllerState);
      // Done action
    }
    void gameAction25() {
      logCurrentState("GameAction25",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelExpenseList); c.setVisibility(View.INVISIBLE);
    }
    void gameAction26() {
      logCurrentState("GameAction26",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelExpenseList); c.setVisibility(View.VISIBLE);
    }
    void gameAction27() {
      logCurrentState("GameAction27",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelOfflineActions); c.setVisibility(View.INVISIBLE);
    }
    void gameAction28() {
      logCurrentState("GameAction28",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelOfflineActions); c.setVisibility(View.VISIBLE);
    }
    void gameAction29() {
      logCurrentState("GameAction29",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAdmin); c.setVisibility(View.INVISIBLE);
    }
    void gameAction30() {
      logCurrentState("GameAction30",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAdmin); c.setVisibility(View.VISIBLE);
    }
    void gameAction31() {
      logCurrentState("GameAction31",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelExpensesToApprove); c.setVisibility(View.INVISIBLE);
    }
    void gameAction32() {
      logCurrentState("GameAction32",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelExpensesToApprove); c.setVisibility(View.VISIBLE);
    }
    void gameAction33() {
      logCurrentState("GameAction33",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelOverview); c.setVisibility(View.INVISIBLE);
    }
    void gameAction34() {
      logCurrentState("GameAction34",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelOverview); c.setVisibility(View.VISIBLE);
    }
    void gameAction35() {
      logCurrentState("GameAction35",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAddExpense); c.setVisibility(View.INVISIBLE);
    }
    void gameAction36() {
      logCurrentState("GameAction36",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAddExpense); c.setVisibility(View.VISIBLE);
    }
    void gameAction37() {
      logCurrentState("GameAction37",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewExpenseValue); b.setEnabled(true);
    }
    void gameAction38() {
      logCurrentState("GameAction38",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewExpenseValue); b.setEnabled(false);
    }
    void gameAction39() {
      logCurrentState("GameAction39",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewExpenseText); b.setEnabled(true);
    }
    void gameAction40() {
      logCurrentState("GameAction40",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewExpenseText); b.setEnabled(false);
    }
    void gameAction41() {
      logCurrentState("GameAction41",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddExpense); b.setEnabled(true);
    }
    void gameAction42() {
      logCurrentState("GameAction42",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddExpense); b.setEnabled(false);
    }
    void gameAction43() {
      logCurrentState("GameAction43",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelReimbursement); c.setVisibility(View.INVISIBLE);
    }
    void gameAction44() {
      logCurrentState("GameAction44",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelReimbursement); c.setVisibility(View.VISIBLE);
    }
    void gameAction45() {
      logCurrentState("GameAction45",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewReimbursementValue); b.setEnabled(true);
    }
    void gameAction46() {
      logCurrentState("GameAction46",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewReimbursementValue); b.setEnabled(false);
    }
    void gameAction47() {
      logCurrentState("GameAction47",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonReimburse); b.setEnabled(true);
    }
    void gameAction48() {
      logCurrentState("GameAction48",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonReimburse); b.setEnabled(false);
    }
    void gameAction49() {
      logCurrentState("GameAction49",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAddTeam); c.setVisibility(View.INVISIBLE);
    }
    void gameAction50() {
      logCurrentState("GameAction50",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelAddTeam); c.setVisibility(View.VISIBLE);
    }
    void gameAction51() {
      logCurrentState("GameAction51",currentSystemGoal,controllerState);
      final ListView b = findViewById(R.id.memberList); b.setEnabled(true);
    }
    void gameAction52() {
      logCurrentState("GameAction52",currentSystemGoal,controllerState);
      final ListView b = findViewById(R.id.memberList); b.setEnabled(false);
    }
    void gameAction53() {
      logCurrentState("GameAction53",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewMemberName); b.setEnabled(true);
    }
    void gameAction54() {
      logCurrentState("GameAction54",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewMemberName); b.setEnabled(false);
    }
    void gameAction55() {
      logCurrentState("GameAction55",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddMember); b.setEnabled(true);
    }
    void gameAction56() {
      logCurrentState("GameAction56",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddMember); b.setEnabled(false);
    }
    void gameAction57() {
      logCurrentState("GameAction57",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddTeam); b.setEnabled(true);
    }
    void gameAction58() {
      logCurrentState("GameAction58",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonAddTeam); b.setEnabled(false);
    }
    void gameAction59() {
      logCurrentState("GameAction59",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelSelectAccount); c.setVisibility(View.INVISIBLE);
    }
    void gameAction60() {
      logCurrentState("GameAction60",currentSystemGoal,controllerState);
      final GridLayout c = findViewById(R.id.PanelSelectAccount); c.setVisibility(View.VISIBLE);
    }
    void gameAction61() {
      logCurrentState("GameAction61",currentSystemGoal,controllerState);
      final ListView b = findViewById(R.id.listViewAvailableTeams); b.setEnabled(true);
    }
    void gameAction62() {
      logCurrentState("GameAction62",currentSystemGoal,controllerState);
      final ListView b = findViewById(R.id.listViewAvailableTeams); b.setEnabled(false);
    }
    void gameAction63() {
      logCurrentState("GameAction63",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewTeamLogin); b.setEnabled(true);
    }
    void gameAction64() {
      logCurrentState("GameAction64",currentSystemGoal,controllerState);
      final EditText b = findViewById(R.id.editTextNewTeamLogin); b.setEnabled(false);
    }
    void gameAction65() {
      logCurrentState("GameAction65",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonLoginTeam); b.setEnabled(true);
    }
    void gameAction66() {
      logCurrentState("GameAction66",currentSystemGoal,controllerState);
      final Button b = findViewById(R.id.buttonLoginTeam); b.setEnabled(false);
    }
    void gameAction67() {
      logCurrentState("GameAction67",currentSystemGoal,controllerState);
      new java.lang.Thread() { public void run() {
          registerNewTeam();
          runOnUiThread(new java.lang.Thread() { public void run() {
             registerNewTeam_onTerminate();
          }});
      }}.start();
    }
    void gameAction68() {
      logCurrentState("GameAction68",currentSystemGoal,controllerState);
      new java.lang.Thread() { public void run() {
          loadTeam();
          runOnUiThread(new java.lang.Thread() { public void run() {
             loadTeam_onTerminate();
          }});
      }}.start();
    }
    void gameAction69() {
      logCurrentState("GameAction69",currentSystemGoal,controllerState);
      new java.lang.Thread() { public void run() {
          storeData();
          runOnUiThread(new java.lang.Thread() { public void run() {
             storeData_onTerminate();
          }});
      }}.start();
    }
    void gameAction70() {
      logCurrentState("GameAction70",currentSystemGoal,controllerState);
 activateMenuItemsOtherThanSelectAccountAndNewAccount();
    }
    void gameAction71() {
      logCurrentState("GameAction71",currentSystemGoal,controllerState);
 deactivateMenuItemsOtherThanSelectAccountAndNewAccount();
    }
    void gameAction72() {
      logCurrentState("GameAction72",currentSystemGoal,controllerState);
 addMemberToTeam();
    }
    void gameAction73() {
      logCurrentState("GameAction73",currentSystemGoal,controllerState);
 addTeamToTeamList();
    }
    void gameAction74() {
      logCurrentState("GameAction74",currentSystemGoal,controllerState);
 updateUnstoredDataView();
    }
    void gameAction75() {
      logCurrentState("GameAction75",currentSystemGoal,controllerState);
 addExpense();
    }
    void gameAction76() {
      logCurrentState("GameAction76",currentSystemGoal,controllerState);
 transferMoney();
    }
    void gameAction77() {
      logCurrentState("GameAction77",currentSystemGoal,controllerState);
 updateAvailableTeamsList();
    }
  void logCurrentState(String inputAction, int currentGoal, int controllerState) {
      String a = inputAction+" ("+Integer.toString(controllerState);
      a = a + ") with goal "+Integer.toString(currentGoal);
      Log.w("Action",a);
 }
int currentSystemGoal = 0;
int controllerState = 0;
void onInputAction0() {
  logCurrentState("inputActioninit",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 0 1 3 5 7 9 11 19 25 35 36 47 0 1 182");
currentSystemGoal = 1;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 0 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 0 33 0 1 290");
currentSystemGoal = 1;
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 0 33 0 1 294");
currentSystemGoal = 1;
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 0 33 0 1 300");
currentSystemGoal = 1;
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 0 33 0 1 303");
currentSystemGoal = 1;
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 0 33 0 1 307");
currentSystemGoal = 1;
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 0 33 0 1 309");
currentSystemGoal = 1;
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 0 33 44 0 1 296");
currentSystemGoal = 1;
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 0 33 44 0 1 304");
currentSystemGoal = 1;
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 0 0 1 219");
currentSystemGoal = 1;
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 0 0 1 213");
currentSystemGoal = 1;
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 0 44 0 1 239");
currentSystemGoal = 1;
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 0 0 1 229");
currentSystemGoal = 1;
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 0 0 1 235");
currentSystemGoal = 1;
controllerState = 235;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 0 44 0 1 234");
currentSystemGoal = 1;
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 0 0 1 245");
currentSystemGoal = 1;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 0 0 1 270");
currentSystemGoal = 1;
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 0 0 1 276");
currentSystemGoal = 1;
controllerState = 276;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 0 44 0 1 286");
currentSystemGoal = 1;
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 0 0 1 279");
currentSystemGoal = 1;
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 0 0 1 284");
currentSystemGoal = 1;
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 0 44 0 1 283");
currentSystemGoal = 1;
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 0 1 3 5 7 9 11 19 25 35 36 47 0 2 182");
currentSystemGoal = 2;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 0 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 0 33 0 1 290");
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 0 33 0 1 294");
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 0 33 0 1 300");
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 0 33 0 1 303");
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 0 33 0 1 307");
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  inputEventCase0();
}
void inputEventCase0() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 0 33 0 1 309");
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 0 33 44 0 1 296");
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 0 33 44 0 1 304");
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 0 0 2 219");
currentSystemGoal = 2;
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 0 0 2 213");
currentSystemGoal = 2;
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 0 44 0 2 239");
currentSystemGoal = 2;
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 0 0 2 229");
currentSystemGoal = 2;
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 0 0 2 235");
currentSystemGoal = 2;
controllerState = 235;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 0 44 0 2 234");
currentSystemGoal = 2;
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 0 0 2 245");
currentSystemGoal = 2;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 0 0 2 270");
currentSystemGoal = 2;
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 0 0 2 276");
currentSystemGoal = 2;
controllerState = 276;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 0 44 0 2 286");
currentSystemGoal = 2;
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 0 0 2 279");
currentSystemGoal = 2;
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 0 0 2 284");
currentSystemGoal = 2;
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 0 44 0 2 283");
currentSystemGoal = 2;
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 0 1 3 5 7 9 11 19 25 35 36 47 0 3 182");
currentSystemGoal = 3;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 0 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 0 33 0 2 290");
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 0 33 0 2 294");
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 0 33 0 2 300");
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 0 33 0 2 303");
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 0 33 0 2 307");
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 0 33 0 2 309");
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 0 33 44 0 2 296");
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 0 33 44 0 2 304");
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 0 0 3 219");
currentSystemGoal = 3;
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 0 0 3 213");
currentSystemGoal = 3;
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 0 44 0 3 239");
currentSystemGoal = 3;
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 0 0 3 229");
currentSystemGoal = 3;
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 0 0 3 235");
currentSystemGoal = 3;
controllerState = 235;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 0 44 0 3 234");
currentSystemGoal = 3;
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 0 0 3 245");
currentSystemGoal = 3;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 0 0 3 270");
currentSystemGoal = 3;
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 0 0 3 276");
currentSystemGoal = 3;
controllerState = 276;
gameAction0(); gameAction24();
return; }
  inputEventCase1();
}
void inputEventCase1() {
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 0 44 0 3 286");
currentSystemGoal = 3;
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 0 0 3 279");
currentSystemGoal = 3;
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 0 0 3 284");
currentSystemGoal = 3;
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 0 44 0 3 283");
currentSystemGoal = 3;
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 0 1 3 5 7 9 11 19 25 35 36 47 0 4 182");
currentSystemGoal = 4;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 0 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 0 33 0 4 290");
currentSystemGoal = 4;
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 0 33 0 3 294");
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 0 33 0 4 300");
currentSystemGoal = 4;
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 0 33 0 3 303");
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 0 33 0 4 307");
currentSystemGoal = 4;
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 0 33 0 4 309");
currentSystemGoal = 4;
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 0 33 44 0 3 296");
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 0 33 44 0 3 304");
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 0 0 3 219");
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 0 0 4 213");
currentSystemGoal = 4;
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 0 44 0 3 239");
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 0 0 3 229");
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 0 0 4 235");
currentSystemGoal = 4;
controllerState = 235;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 0 44 0 3 234");
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 0 0 4 245");
currentSystemGoal = 4;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 0 0 3 270");
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 0 0 4 276");
currentSystemGoal = 4;
controllerState = 276;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 0 44 0 3 286");
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 0 0 3 279");
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 0 0 4 284");
currentSystemGoal = 4;
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 0 44 0 3 283");
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 0 1 3 5 7 9 11 19 25 35 36 47 0 5 182");
currentSystemGoal = 5;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 0 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 0 33 0 5 290");
currentSystemGoal = 5;
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 0 33 0 5 294");
currentSystemGoal = 5;
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  inputEventCase2();
}
void inputEventCase2() {
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 0 33 0 4 300");
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 0 33 0 4 303");
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 0 33 0 5 307");
currentSystemGoal = 5;
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 0 33 0 4 309");
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 0 33 44 0 5 296");
currentSystemGoal = 5;
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 0 33 44 0 4 304");
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 0 0 5 219");
currentSystemGoal = 5;
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 0 0 4 213");
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 0 44 0 5 239");
currentSystemGoal = 5;
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 0 0 4 229");
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 0 0 4 235");
controllerState = 235;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 0 44 0 4 234");
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 0 0 5 245");
currentSystemGoal = 5;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 0 0 5 270");
currentSystemGoal = 5;
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 0 0 4 276");
controllerState = 276;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 0 44 0 5 286");
currentSystemGoal = 5;
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 0 0 4 279");
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 0 0 4 284");
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 0 44 0 4 283");
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 0 1 3 5 7 9 11 19 25 35 36 47 0 0 182");
currentSystemGoal = 0;
controllerState = 182;
gameAction0(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction71();
gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 0 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 0 33 0 0 290");
currentSystemGoal = 0;
controllerState = 290;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 0 33 0 0 294");
currentSystemGoal = 0;
controllerState = 294;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 0 33 0 0 300");
currentSystemGoal = 0;
controllerState = 300;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 0 33 0 0 303");
currentSystemGoal = 0;
controllerState = 303;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 0 33 0 5 307");
controllerState = 307;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 0 33 0 5 309");
controllerState = 309;
gameAction0(); gameAction57(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 0 33 44 0 0 296");
currentSystemGoal = 0;
controllerState = 296;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 0 33 44 0 0 304");
currentSystemGoal = 0;
controllerState = 304;
gameAction0(); gameAction57(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 0 0 0 219");
currentSystemGoal = 0;
controllerState = 219;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 0 0 0 213");
currentSystemGoal = 0;
controllerState = 213;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 0 44 0 0 239");
currentSystemGoal = 0;
controllerState = 239;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 0 0 0 229");
currentSystemGoal = 0;
controllerState = 229;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 0 0 5 235");
controllerState = 235;
gameAction0(); gameAction24();
return; }
  inputEventCase3();
}
void inputEventCase3() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 0 44 0 0 234");
currentSystemGoal = 0;
controllerState = 234;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 0 0 0 245");
currentSystemGoal = 0;
controllerState = 245;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 0 0 0 270");
currentSystemGoal = 0;
controllerState = 270;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 0 0 0 276");
currentSystemGoal = 0;
controllerState = 276;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 0 44 0 0 286");
currentSystemGoal = 0;
controllerState = 286;
gameAction0(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 0 0 0 279");
currentSystemGoal = 0;
controllerState = 279;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 0 0 5 284");
controllerState = 284;
gameAction0(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 0 44 0 0 283");
currentSystemGoal = 0;
controllerState = 283;
gameAction0(); gameAction68(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction1() {
  logCurrentState("inputActionbuttonAddExpense.click",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 1 51 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 1 51 0 1 182");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 1 51 0 1 184");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 1 51 0 1 187");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 1 51 0 1 188");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 1 51 0 1 190");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 1 51 0 1 203");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 1 51 0 1 204");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 1 51 0 1 205");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 1 51 0 1 206");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 1 51 0 1 215");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 1 51 0 1 217");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 1 51 0 1 221");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 1 51 0 1 224");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 1 51 0 1 227");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 1 51 0 1 231");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 1 51 0 1 246");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 1 51 0 1 266");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 1 51 0 1 268");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 1 51 0 1 272");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 1 51 0 1 274");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 1 51 0 1 278");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 1 51 0 1 281");
currentSystemGoal = 1;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 1 51 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 1 51 0 2 182");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 1 51 0 1 184");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 1 51 0 1 187");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 1 51 0 1 188");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 1 51 0 1 190");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 1 51 0 1 203");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 1 51 0 1 204");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 1 51 0 1 205");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 1 51 0 1 206");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 1 51 0 2 215");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  inputEventCase4();
}
void inputEventCase4() {
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 1 51 0 2 217");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 1 51 0 2 221");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 1 51 0 2 224");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 1 51 0 2 227");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 1 51 0 2 231");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 1 51 0 2 246");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 1 51 0 2 266");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 1 51 0 2 268");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 1 51 0 2 272");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 1 51 0 2 274");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 1 51 0 2 278");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 1 51 0 2 281");
currentSystemGoal = 2;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 1 51 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 1 51 0 3 182");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 1 51 0 2 184");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 1 51 0 2 187");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 1 51 0 2 188");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 1 51 0 2 190");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 1 51 0 2 203");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 1 51 0 2 204");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 1 51 0 2 205");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 1 51 0 2 206");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 1 51 0 3 215");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 1 51 0 3 217");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 1 51 0 3 221");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 1 51 0 3 224");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 1 51 0 3 227");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 1 51 0 3 231");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 1 51 0 3 246");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 1 51 0 3 266");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 1 51 0 3 268");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 1 51 0 3 272");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 1 51 0 3 274");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 1 51 0 3 278");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  inputEventCase5();
}
void inputEventCase5() {
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 1 51 0 3 281");
currentSystemGoal = 3;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 1 51 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 1 51 0 4 182");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 1 51 0 4 184");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 1 51 0 3 187");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 1 51 0 4 188");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 1 51 0 3 190");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 1 51 0 4 203");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 1 51 0 4 204");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 1 51 0 3 205");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 1 51 0 3 206");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 1 51 0 3 215");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 1 51 0 4 217");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 1 51 0 3 221");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 1 51 0 3 224");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 1 51 0 4 227");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 1 51 0 3 231");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 1 51 0 4 246");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 1 51 0 3 266");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 1 51 0 4 268");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 1 51 0 3 272");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 1 51 0 3 274");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 1 51 0 4 278");
currentSystemGoal = 4;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 1 51 0 3 281");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 1 51 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 1 51 0 5 182");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 1 51 0 5 184");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 1 51 0 5 187");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 1 51 0 4 188");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 1 51 0 4 190");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 1 51 0 5 203");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 1 51 0 4 204");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 1 51 0 5 205");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 1 51 0 4 206");
gameAction1(); gameAction75(); gameAction24();
return; }
  inputEventCase6();
}
void inputEventCase6() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 1 51 0 5 215");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 1 51 0 4 217");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 1 51 0 5 221");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 1 51 0 4 224");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 1 51 0 4 227");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 1 51 0 4 231");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 1 51 0 5 246");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 1 51 0 5 266");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 1 51 0 4 268");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 1 51 0 5 272");
currentSystemGoal = 5;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 1 51 0 4 274");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 1 51 0 4 278");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 1 51 0 4 281");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 1 51 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 1 51 0 0 182");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 1 51 0 0 184");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 1 51 0 0 187");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 1 51 0 0 188");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 1 51 0 0 190");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 1 51 0 5 203");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 1 51 0 5 204");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 1 51 0 5 205");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 1 51 0 5 206");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 1 51 0 0 215");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 1 51 0 0 217");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 1 51 0 5 221");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 1 51 0 0 224");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 1 51 0 5 227");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 1 51 0 5 231");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 1 51 0 0 246");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 1 51 0 0 266");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 1 51 0 0 268");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 1 51 0 5 272");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 1 51 0 0 274");
currentSystemGoal = 0;
gameAction1(); gameAction75(); gameAction24();
return; }
  inputEventCase7();
}
void inputEventCase7() {
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 1 51 0 5 278");
gameAction1(); gameAction75(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 1 51 0 5 281");
gameAction1(); gameAction75(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction2() {
  logCurrentState("inputActionbuttonReimburse.click",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 2 52 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 2 52 0 1 182");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 2 52 0 1 184");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 2 52 0 1 187");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 2 52 0 1 188");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 2 52 0 1 190");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 2 52 0 1 203");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 2 52 0 1 204");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 2 52 0 1 205");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 2 52 0 1 206");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 2 52 0 1 215");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 2 52 0 1 217");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 2 52 0 1 221");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 2 52 0 1 224");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 2 52 0 1 227");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 2 52 0 1 231");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 2 52 0 1 246");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 2 52 0 1 266");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 2 52 0 1 268");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 2 52 0 1 272");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 2 52 0 1 274");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 2 52 0 1 278");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 2 52 0 1 281");
currentSystemGoal = 1;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 2 52 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 2 52 0 2 182");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 2 52 0 1 184");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 2 52 0 1 187");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 2 52 0 1 188");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 2 52 0 1 190");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 2 52 0 1 203");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 2 52 0 1 204");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 2 52 0 1 205");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 2 52 0 1 206");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 2 52 0 2 215");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  inputEventCase8();
}
void inputEventCase8() {
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 2 52 0 2 217");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 2 52 0 2 221");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 2 52 0 2 224");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 2 52 0 2 227");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 2 52 0 2 231");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 2 52 0 2 246");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 2 52 0 2 266");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 2 52 0 2 268");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 2 52 0 2 272");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 2 52 0 2 274");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 2 52 0 2 278");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 2 52 0 2 281");
currentSystemGoal = 2;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 2 52 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 2 52 0 3 182");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 2 52 0 2 184");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 2 52 0 2 187");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 2 52 0 2 188");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 2 52 0 2 190");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 2 52 0 2 203");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 2 52 0 2 204");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 2 52 0 2 205");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 2 52 0 2 206");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 2 52 0 3 215");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 2 52 0 3 217");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 2 52 0 3 221");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 2 52 0 3 224");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 2 52 0 3 227");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 2 52 0 3 231");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 2 52 0 3 246");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 2 52 0 3 266");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 2 52 0 3 268");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 2 52 0 3 272");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 2 52 0 3 274");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 2 52 0 3 278");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  inputEventCase9();
}
void inputEventCase9() {
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 2 52 0 3 281");
currentSystemGoal = 3;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 2 52 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 2 52 0 4 182");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 2 52 0 4 184");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 2 52 0 3 187");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 2 52 0 4 188");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 2 52 0 3 190");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 2 52 0 4 203");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 2 52 0 4 204");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 2 52 0 3 205");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 2 52 0 3 206");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 2 52 0 3 215");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 2 52 0 4 217");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 2 52 0 3 221");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 2 52 0 3 224");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 2 52 0 4 227");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 2 52 0 3 231");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 2 52 0 4 246");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 2 52 0 3 266");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 2 52 0 4 268");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 2 52 0 3 272");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 2 52 0 3 274");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 2 52 0 4 278");
currentSystemGoal = 4;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 2 52 0 3 281");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 2 52 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 2 52 0 5 182");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 2 52 0 5 184");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 2 52 0 5 187");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 2 52 0 4 188");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 2 52 0 4 190");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 2 52 0 5 203");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 2 52 0 4 204");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 2 52 0 5 205");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 2 52 0 4 206");
gameAction2(); gameAction76(); gameAction24();
return; }
  inputEventCase10();
}
void inputEventCase10() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 2 52 0 5 215");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 2 52 0 4 217");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 2 52 0 5 221");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 2 52 0 4 224");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 2 52 0 4 227");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 2 52 0 4 231");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 2 52 0 5 246");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 2 52 0 5 266");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 2 52 0 4 268");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 2 52 0 5 272");
currentSystemGoal = 5;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 2 52 0 4 274");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 2 52 0 4 278");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 2 52 0 4 281");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 2 52 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 2 52 0 0 182");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 2 52 0 0 184");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 2 52 0 0 187");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 2 52 0 0 188");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 2 52 0 0 190");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 2 52 0 5 203");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 2 52 0 5 204");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 2 52 0 5 205");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 2 52 0 5 206");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 2 52 0 0 215");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 2 52 0 0 217");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 2 52 0 5 221");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 2 52 0 0 224");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 2 52 0 5 227");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 2 52 0 5 231");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 2 52 0 0 246");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 2 52 0 0 266");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 2 52 0 0 268");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 2 52 0 5 272");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 2 52 0 0 274");
currentSystemGoal = 0;
gameAction2(); gameAction76(); gameAction24();
return; }
  inputEventCase11();
}
void inputEventCase11() {
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 2 52 0 5 278");
gameAction2(); gameAction76(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 2 52 0 5 281");
gameAction2(); gameAction76(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction3() {
  logCurrentState("inputActionbuttonAddMember.click",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 3 48 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 3 48 0 1 182");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 3 48 0 1 184");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 3 48 0 1 187");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 3 48 0 1 188");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 3 48 0 1 190");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 3 48 0 1 203");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 3 48 0 1 204");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 3 48 0 1 205");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 3 48 0 1 206");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 3 48 0 1 215");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 3 48 0 1 217");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 3 48 0 1 221");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 3 48 0 1 224");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 3 48 0 1 227");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 3 48 0 1 231");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 3 48 0 1 246");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 3 48 0 1 266");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 3 48 0 1 268");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 3 48 0 1 272");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 3 48 0 1 274");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 3 48 0 1 278");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 3 48 0 1 281");
currentSystemGoal = 1;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 3 48 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 3 48 0 2 182");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 3 48 0 1 184");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 3 48 0 1 187");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 3 48 0 1 188");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 3 48 0 1 190");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 3 48 0 1 203");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 3 48 0 1 204");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 3 48 0 1 205");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 3 48 0 1 206");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 3 48 0 2 215");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  inputEventCase12();
}
void inputEventCase12() {
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 3 48 0 2 217");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 3 48 0 2 221");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 3 48 0 2 224");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 3 48 0 2 227");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 3 48 0 2 231");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 3 48 0 2 246");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 3 48 0 2 266");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 3 48 0 2 268");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 3 48 0 2 272");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 3 48 0 2 274");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 3 48 0 2 278");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 3 48 0 2 281");
currentSystemGoal = 2;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 3 48 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 3 48 0 3 182");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 3 48 0 2 184");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 3 48 0 2 187");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 3 48 0 2 188");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 3 48 0 2 190");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 3 48 0 2 203");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 3 48 0 2 204");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 3 48 0 2 205");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 3 48 0 2 206");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 3 48 0 3 215");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 3 48 0 3 217");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 3 48 0 3 221");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 3 48 0 3 224");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 3 48 0 3 227");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 3 48 0 3 231");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 3 48 0 3 246");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 3 48 0 3 266");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 3 48 0 3 268");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 3 48 0 3 272");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 3 48 0 3 274");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 3 48 0 3 278");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  inputEventCase13();
}
void inputEventCase13() {
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 3 48 0 3 281");
currentSystemGoal = 3;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 3 48 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 3 48 0 4 182");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 3 48 0 4 184");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 3 48 0 3 187");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 3 48 0 4 188");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 3 48 0 3 190");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 3 48 0 4 203");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 3 48 0 4 204");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 3 48 0 3 205");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 3 48 0 3 206");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 3 48 0 3 215");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 3 48 0 4 217");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 3 48 0 3 221");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 3 48 0 3 224");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 3 48 0 4 227");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 3 48 0 3 231");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 3 48 0 4 246");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 3 48 0 3 266");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 3 48 0 4 268");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 3 48 0 3 272");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 3 48 0 3 274");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 3 48 0 4 278");
currentSystemGoal = 4;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 3 48 0 3 281");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 3 48 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 3 48 0 5 182");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 3 48 0 5 184");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 3 48 0 5 187");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 3 48 0 4 188");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 3 48 0 4 190");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 3 48 0 5 203");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 3 48 0 4 204");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 3 48 0 5 205");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 3 48 0 4 206");
gameAction3(); gameAction72(); gameAction24();
return; }
  inputEventCase14();
}
void inputEventCase14() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 3 48 0 5 215");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 3 48 0 4 217");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 3 48 0 5 221");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 3 48 0 4 224");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 3 48 0 4 227");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 3 48 0 4 231");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 3 48 0 5 246");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 3 48 0 5 266");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 3 48 0 4 268");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 3 48 0 5 272");
currentSystemGoal = 5;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 3 48 0 4 274");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 3 48 0 4 278");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 3 48 0 4 281");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 3 48 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 3 48 0 0 182");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 3 48 0 0 184");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 3 48 0 0 187");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 3 48 0 0 188");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 3 48 0 0 190");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 3 48 0 5 203");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 3 48 0 5 204");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 3 48 0 5 205");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 3 48 0 5 206");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 3 48 0 0 215");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 3 48 0 0 217");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 3 48 0 5 221");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 3 48 0 0 224");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 3 48 0 5 227");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 3 48 0 5 231");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 3 48 0 0 246");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 3 48 0 0 266");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 3 48 0 0 268");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 3 48 0 5 272");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 3 48 0 0 274");
currentSystemGoal = 0;
gameAction3(); gameAction72(); gameAction24();
return; }
  inputEventCase15();
}
void inputEventCase15() {
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 3 48 0 5 278");
gameAction3(); gameAction72(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 3 48 0 5 281");
gameAction3(); gameAction72(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction4() {
  logCurrentState("inputActionbuttonAddTeam.click",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 4 0 1 210");
currentSystemGoal = 1;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 4 34 43 0 1 184");
currentSystemGoal = 1;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 4 33 43 0 1 291");
currentSystemGoal = 1;
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 4 33 43 0 1 296");
currentSystemGoal = 1;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 4 33 43 0 1 301");
currentSystemGoal = 1;
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 4 33 43 0 1 304");
currentSystemGoal = 1;
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 4 33 43 0 1 308");
currentSystemGoal = 1;
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 4 33 43 0 1 310");
currentSystemGoal = 1;
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 4 33 43 44 0 1 296");
currentSystemGoal = 1;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 4 33 43 44 0 1 304");
currentSystemGoal = 1;
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 4 34 43 0 1 187");
currentSystemGoal = 1;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 4 34 43 0 1 188");
currentSystemGoal = 1;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 4 34 43 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 4 34 43 0 1 190");
currentSystemGoal = 1;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 4 34 43 0 1 204");
currentSystemGoal = 1;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 4 34 43 0 1 206");
currentSystemGoal = 1;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 4 34 43 0 1 184");
currentSystemGoal = 1;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 4 34 43 0 1 187");
currentSystemGoal = 1;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 4 34 43 0 1 188");
currentSystemGoal = 1;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 4 34 43 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 4 34 43 0 1 190");
currentSystemGoal = 1;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 4 34 43 0 1 204");
currentSystemGoal = 1;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 4 34 43 0 1 206");
currentSystemGoal = 1;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 4 0 2 210");
currentSystemGoal = 2;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 4 34 43 0 2 184");
currentSystemGoal = 2;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 4 33 43 0 1 291");
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  inputEventCase16();
}
void inputEventCase16() {
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 4 33 43 0 1 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 4 33 43 0 1 301");
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 4 33 43 0 1 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 4 33 43 0 1 308");
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 4 33 43 0 1 310");
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 4 33 43 44 0 1 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 4 33 43 44 0 1 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 4 34 43 0 2 187");
currentSystemGoal = 2;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 4 34 43 0 2 188");
currentSystemGoal = 2;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 4 34 43 0 2 205");
currentSystemGoal = 2;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 4 34 43 0 2 190");
currentSystemGoal = 2;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 4 34 43 0 2 204");
currentSystemGoal = 2;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 4 34 43 0 2 206");
currentSystemGoal = 2;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 4 34 43 0 2 184");
currentSystemGoal = 2;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 4 34 43 0 2 187");
currentSystemGoal = 2;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 4 34 43 0 2 188");
currentSystemGoal = 2;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 4 34 43 0 2 205");
currentSystemGoal = 2;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 4 34 43 0 2 190");
currentSystemGoal = 2;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 4 34 43 0 2 204");
currentSystemGoal = 2;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 4 34 43 0 2 206");
currentSystemGoal = 2;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 4 0 3 210");
currentSystemGoal = 3;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 4 34 43 0 3 184");
currentSystemGoal = 3;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 4 33 43 0 2 291");
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 4 33 43 0 2 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 4 33 43 0 2 301");
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 4 33 43 0 2 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  inputEventCase17();
}
void inputEventCase17() {
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 4 33 43 0 2 308");
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 4 33 43 0 2 310");
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 4 33 43 44 0 2 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 4 33 43 44 0 2 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 4 34 43 0 3 187");
currentSystemGoal = 3;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 4 34 43 0 3 188");
currentSystemGoal = 3;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 4 34 43 0 3 205");
currentSystemGoal = 3;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 4 34 43 0 3 190");
currentSystemGoal = 3;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 4 34 43 0 3 204");
currentSystemGoal = 3;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 4 34 43 0 3 206");
currentSystemGoal = 3;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 4 34 43 0 3 184");
currentSystemGoal = 3;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 4 34 43 0 3 187");
currentSystemGoal = 3;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 4 34 43 0 3 188");
currentSystemGoal = 3;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 4 34 43 0 3 205");
currentSystemGoal = 3;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 4 34 43 0 3 190");
currentSystemGoal = 3;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 4 34 43 0 3 204");
currentSystemGoal = 3;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 4 34 43 0 3 206");
currentSystemGoal = 3;
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 4 0 4 210");
currentSystemGoal = 4;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 4 34 43 0 4 184");
currentSystemGoal = 4;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 4 33 43 0 4 291");
currentSystemGoal = 4;
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 4 33 43 0 3 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 4 33 43 0 4 301");
currentSystemGoal = 4;
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 4 33 43 0 3 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 4 33 43 0 4 308");
currentSystemGoal = 4;
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 4 33 43 0 4 310");
currentSystemGoal = 4;
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 4 33 43 44 0 3 296");
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  inputEventCase18();
}
void inputEventCase18() {
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 4 33 43 44 0 3 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 4 34 43 0 3 187");
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 4 34 43 0 4 188");
currentSystemGoal = 4;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 4 34 43 0 3 205");
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 4 34 43 0 3 190");
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 4 34 43 0 4 204");
currentSystemGoal = 4;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 4 34 43 0 3 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 4 34 43 0 4 184");
currentSystemGoal = 4;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 4 34 43 0 3 187");
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 4 34 43 0 4 188");
currentSystemGoal = 4;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 4 34 43 0 3 205");
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 4 34 43 0 3 190");
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 4 34 43 0 4 204");
currentSystemGoal = 4;
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 4 34 43 0 3 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 4 0 5 210");
currentSystemGoal = 5;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 4 34 43 0 5 184");
currentSystemGoal = 5;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 4 33 43 0 5 291");
currentSystemGoal = 5;
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 4 33 43 0 5 296");
currentSystemGoal = 5;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 4 33 43 0 4 301");
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 4 33 43 0 4 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 4 33 43 0 5 308");
currentSystemGoal = 5;
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 4 33 43 0 4 310");
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 4 33 43 44 0 5 296");
currentSystemGoal = 5;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 4 33 43 44 0 4 304");
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 4 34 43 0 5 187");
currentSystemGoal = 5;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  inputEventCase19();
}
void inputEventCase19() {
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 4 34 43 0 4 188");
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 4 34 43 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 4 34 43 0 4 190");
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 4 34 43 0 4 204");
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 4 34 43 0 4 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 4 34 43 0 5 184");
currentSystemGoal = 5;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 4 34 43 0 5 187");
currentSystemGoal = 5;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 4 34 43 0 4 188");
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 4 34 43 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 4 34 43 0 4 190");
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 4 34 43 0 4 204");
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 4 34 43 0 4 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 4 0 0 210");
currentSystemGoal = 0;
controllerState = 210;
gameAction4(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 4 34 43 0 0 184");
currentSystemGoal = 0;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 4 33 43 0 0 291");
currentSystemGoal = 0;
controllerState = 291;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 4 33 43 0 0 296");
currentSystemGoal = 0;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 4 33 43 0 0 301");
currentSystemGoal = 0;
controllerState = 301;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 4 33 43 0 0 304");
currentSystemGoal = 0;
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 4 33 43 0 5 308");
controllerState = 308;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 4 33 43 0 5 310");
controllerState = 310;
gameAction4(); gameAction57(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 4 33 43 44 0 0 296");
currentSystemGoal = 0;
controllerState = 296;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 4 33 43 44 0 0 304");
currentSystemGoal = 0;
controllerState = 304;
gameAction4(); gameAction57(); gameAction67(); gameAction68();
gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 4 34 43 0 0 187");
currentSystemGoal = 0;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 4 34 43 0 0 188");
currentSystemGoal = 0;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 4 34 43 0 5 205");
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 4 34 43 0 0 190");
currentSystemGoal = 0;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  inputEventCase20();
}
void inputEventCase20() {
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 4 34 43 0 5 204");
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 4 34 43 0 5 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 4 34 43 0 0 184");
currentSystemGoal = 0;
controllerState = 184;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 4 34 43 0 0 187");
currentSystemGoal = 0;
controllerState = 187;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 4 34 43 0 0 188");
currentSystemGoal = 0;
controllerState = 188;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 4 34 43 0 5 205");
controllerState = 205;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 4 34 43 0 0 190");
currentSystemGoal = 0;
controllerState = 190;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 4 34 43 0 5 204");
controllerState = 204;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 4 34 43 0 5 206");
controllerState = 206;
gameAction4(); gameAction58(); gameAction67(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction5() {
  logCurrentState("inputActionlistViewAvailableTeams.selected",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 5 0 1 211");
currentSystemGoal = 1;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 5 44 0 1 215");
currentSystemGoal = 1;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 5 0 1 203");
currentSystemGoal = 1;
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 5 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 5 0 1 204");
currentSystemGoal = 1;
controllerState = 204;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 5 0 1 206");
currentSystemGoal = 1;
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 5 0 1 203");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 5 0 1 204");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 5 0 1 205");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 5 0 1 206");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 5 0 1 221");
currentSystemGoal = 1;
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 5 0 1 227");
currentSystemGoal = 1;
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 5 0 1 221");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 5 0 1 231");
currentSystemGoal = 1;
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 5 0 1 227");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 5 0 1 231");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 5 44 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 5 0 1 272");
currentSystemGoal = 1;
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 5 0 1 278");
currentSystemGoal = 1;
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 5 0 1 272");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 5 0 1 281");
currentSystemGoal = 1;
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 5 0 1 278");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 5 0 1 281");
currentSystemGoal = 1;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 5 0 2 211");
currentSystemGoal = 2;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 5 44 0 2 215");
currentSystemGoal = 2;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 5 0 1 203");
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 5 0 1 205");
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 5 0 1 204");
controllerState = 204;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 5 0 1 206");
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 5 0 1 203");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 5 0 1 204");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 5 0 1 205");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 5 0 1 206");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 5 0 2 221");
currentSystemGoal = 2;
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 5 0 2 227");
currentSystemGoal = 2;
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 5 0 2 221");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 5 0 2 231");
currentSystemGoal = 2;
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 5 0 2 227");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 5 0 2 231");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 5 44 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 5 0 2 272");
currentSystemGoal = 2;
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 5 0 2 278");
currentSystemGoal = 2;
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 5 0 2 272");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 5 0 2 281");
currentSystemGoal = 2;
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 5 0 2 278");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 5 0 2 281");
currentSystemGoal = 2;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 5 0 3 211");
currentSystemGoal = 3;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 5 44 0 3 215");
currentSystemGoal = 3;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  inputEventCase21();
}
void inputEventCase21() {
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 5 0 2 203");
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 5 0 2 205");
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 5 0 2 204");
controllerState = 204;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 5 0 2 206");
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 5 0 2 203");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 5 0 2 204");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 5 0 2 205");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 5 0 2 206");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 5 0 3 221");
currentSystemGoal = 3;
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 5 0 3 227");
currentSystemGoal = 3;
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 5 0 3 221");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 5 0 3 231");
currentSystemGoal = 3;
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 5 0 3 227");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 5 0 3 231");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 5 44 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 5 0 3 272");
currentSystemGoal = 3;
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 5 0 3 278");
currentSystemGoal = 3;
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 5 0 3 272");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 5 0 3 281");
currentSystemGoal = 3;
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 5 0 3 278");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 5 0 3 281");
currentSystemGoal = 3;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 5 0 4 211");
currentSystemGoal = 4;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 5 44 0 4 215");
currentSystemGoal = 4;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 5 0 4 203");
currentSystemGoal = 4;
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 5 0 3 205");
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 5 0 4 204");
currentSystemGoal = 4;
controllerState = 204;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 5 0 3 206");
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 5 0 4 203");
currentSystemGoal = 4;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 5 0 4 204");
currentSystemGoal = 4;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 5 0 3 205");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 5 0 3 206");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 5 0 3 221");
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 5 0 4 227");
currentSystemGoal = 4;
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 5 0 3 221");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 5 0 3 231");
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 5 0 4 227");
currentSystemGoal = 4;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 5 0 3 231");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 5 44 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 5 0 3 272");
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 5 0 4 278");
currentSystemGoal = 4;
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 5 0 3 272");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 5 0 3 281");
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 5 0 4 278");
currentSystemGoal = 4;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 5 0 3 281");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 5 0 5 211");
currentSystemGoal = 5;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 5 44 0 5 215");
currentSystemGoal = 5;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 5 0 5 203");
currentSystemGoal = 5;
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 5 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 5 0 4 204");
controllerState = 204;
gameAction5(); gameAction24();
return; }
  inputEventCase22();
}
void inputEventCase22() {
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 5 0 4 206");
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 5 0 5 203");
currentSystemGoal = 5;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 5 0 4 204");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 5 0 5 205");
currentSystemGoal = 5;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 5 0 4 206");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 5 0 5 221");
currentSystemGoal = 5;
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 5 0 4 227");
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 5 0 5 221");
currentSystemGoal = 5;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 5 0 4 231");
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 5 0 4 227");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 5 0 4 231");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 5 44 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 5 0 5 272");
currentSystemGoal = 5;
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 5 0 4 278");
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 5 0 5 272");
currentSystemGoal = 5;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 5 0 4 281");
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 5 0 4 278");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 5 0 4 281");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 5 0 0 211");
currentSystemGoal = 0;
controllerState = 211;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 5 44 0 0 215");
currentSystemGoal = 0;
controllerState = 215;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 5 0 0 203");
currentSystemGoal = 0;
controllerState = 203;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 5 0 0 205");
currentSystemGoal = 0;
controllerState = 205;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 5 0 0 204");
currentSystemGoal = 0;
controllerState = 204;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 5 0 0 206");
currentSystemGoal = 0;
controllerState = 206;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 5 0 5 203");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 5 0 5 204");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 5 0 5 205");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 5 0 5 206");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 5 0 0 221");
currentSystemGoal = 0;
controllerState = 221;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 5 0 0 227");
currentSystemGoal = 0;
controllerState = 227;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 5 0 5 221");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 5 0 0 231");
currentSystemGoal = 0;
controllerState = 231;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 5 0 5 227");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 5 0 5 231");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 5 44 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction5(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 5 0 0 272");
currentSystemGoal = 0;
controllerState = 272;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 5 0 0 278");
currentSystemGoal = 0;
controllerState = 278;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 5 0 5 272");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 5 0 0 281");
currentSystemGoal = 0;
controllerState = 281;
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 5 0 5 278");
gameAction5(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 5 0 5 281");
gameAction5(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction6() {
  logCurrentState("inputActionbuttonLoginTeam.click",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 6 49 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 6 49 0 1 182");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 6 49 0 1 184");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 6 49 0 1 187");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 6 49 0 1 188");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 6 49 0 1 190");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 6 49 0 1 203");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 6 49 0 1 204");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 6 49 0 1 205");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 6 49 0 1 206");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 6 49 0 1 215");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 6 49 0 1 217");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 6 49 0 1 221");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 6 49 0 1 224");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 6 49 0 1 227");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 6 49 0 1 231");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 6 49 0 1 246");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 6 49 0 1 266");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 6 49 0 1 268");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 6 49 0 1 272");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 6 49 0 1 274");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 6 49 0 1 278");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 6 49 0 1 281");
currentSystemGoal = 1;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 6 49 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 6 49 0 2 182");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 6 49 0 1 184");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 6 49 0 1 187");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 6 49 0 1 188");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 6 49 0 1 190");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 6 49 0 1 203");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 6 49 0 1 204");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 6 49 0 1 205");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 6 49 0 1 206");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 6 49 0 2 215");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  inputEventCase23();
}
void inputEventCase23() {
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 6 49 0 2 217");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 6 49 0 2 221");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 6 49 0 2 224");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 6 49 0 2 227");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 6 49 0 2 231");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 6 49 0 2 246");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 6 49 0 2 266");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 6 49 0 2 268");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 6 49 0 2 272");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 6 49 0 2 274");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 6 49 0 2 278");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 6 49 0 2 281");
currentSystemGoal = 2;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 6 49 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 6 49 0 3 182");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 6 49 0 2 184");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 6 49 0 2 187");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 6 49 0 2 188");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 6 49 0 2 190");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 6 49 0 2 203");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 6 49 0 2 204");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 6 49 0 2 205");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 6 49 0 2 206");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 6 49 0 3 215");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 6 49 0 3 217");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 6 49 0 3 221");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 6 49 0 3 224");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 6 49 0 3 227");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 6 49 0 3 231");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 6 49 0 3 246");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 6 49 0 3 266");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 6 49 0 3 268");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 6 49 0 3 272");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 6 49 0 3 274");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 6 49 0 3 278");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  inputEventCase24();
}
void inputEventCase24() {
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 6 49 0 3 281");
currentSystemGoal = 3;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 6 49 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 6 49 0 4 182");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 6 49 0 4 184");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 6 49 0 3 187");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 6 49 0 4 188");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 6 49 0 3 190");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 6 49 0 4 203");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 6 49 0 4 204");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 6 49 0 3 205");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 6 49 0 3 206");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 6 49 0 3 215");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 6 49 0 4 217");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 6 49 0 3 221");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 6 49 0 3 224");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 6 49 0 4 227");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 6 49 0 3 231");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 6 49 0 4 246");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 6 49 0 3 266");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 6 49 0 4 268");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 6 49 0 3 272");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 6 49 0 3 274");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 6 49 0 4 278");
currentSystemGoal = 4;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 6 49 0 3 281");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 6 49 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 6 49 0 5 182");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 6 49 0 5 184");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 6 49 0 5 187");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 6 49 0 4 188");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 6 49 0 4 190");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 6 49 0 5 203");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 6 49 0 4 204");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 6 49 0 5 205");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 6 49 0 4 206");
gameAction6(); gameAction73(); gameAction24();
return; }
  inputEventCase25();
}
void inputEventCase25() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 6 49 0 5 215");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 6 49 0 4 217");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 6 49 0 5 221");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 6 49 0 4 224");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 6 49 0 4 227");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 6 49 0 4 231");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 6 49 0 5 246");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 6 49 0 5 266");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 6 49 0 4 268");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 6 49 0 5 272");
currentSystemGoal = 5;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 6 49 0 4 274");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 6 49 0 4 278");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 6 49 0 4 281");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 6 49 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 6 49 0 0 182");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 6 49 0 0 184");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 6 49 0 0 187");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 6 49 0 0 188");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 6 49 0 0 190");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 6 49 0 5 203");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 6 49 0 5 204");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 6 49 0 5 205");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 6 49 0 5 206");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 6 49 0 0 215");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 6 49 0 0 217");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 6 49 0 5 221");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 6 49 0 0 224");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 6 49 0 5 227");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 6 49 0 5 231");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 6 49 0 0 246");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 6 49 0 0 266");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 6 49 0 0 268");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 6 49 0 5 272");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 6 49 0 0 274");
currentSystemGoal = 0;
gameAction6(); gameAction73(); gameAction24();
return; }
  inputEventCase26();
}
void inputEventCase26() {
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 6 49 0 5 278");
gameAction6(); gameAction73(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 6 49 0 5 281");
gameAction6(); gameAction73(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
void onInputAction7() {
  logCurrentState("inputActionregisterNewTeam.terminates",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 7 0 1 212");
currentSystemGoal = 1;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 7 0 1 212");
currentSystemGoal = 1;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 7 33 53 0 1 246");
currentSystemGoal = 1;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 7 33 53 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 7 33 53 0 1 268");
currentSystemGoal = 1;
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 7 33 53 0 1 274");
currentSystemGoal = 1;
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 7 33 44 53 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 7 33 53 0 1 278");
currentSystemGoal = 1;
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 7 33 53 0 1 272");
currentSystemGoal = 1;
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 7 33 53 0 1 281");
currentSystemGoal = 1;
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 7 0 1 223");
currentSystemGoal = 1;
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 7 0 1 228");
currentSystemGoal = 1;
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 7 44 0 1 241");
currentSystemGoal = 1;
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 7 0 1 233");
currentSystemGoal = 1;
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 7 0 1 238");
currentSystemGoal = 1;
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 7 44 0 1 237");
currentSystemGoal = 1;
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 7 0 1 246");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 7 0 1 266");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 7 0 1 268");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 7 0 1 272");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 7 0 1 274");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 7 0 1 278");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 7 0 1 281");
currentSystemGoal = 1;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 7 0 2 212");
currentSystemGoal = 2;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 7 0 2 212");
currentSystemGoal = 2;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 7 33 53 0 2 246");
currentSystemGoal = 2;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 7 33 53 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 7 33 53 0 2 268");
currentSystemGoal = 2;
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 7 33 53 0 2 274");
currentSystemGoal = 2;
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 7 33 44 53 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 7 33 53 0 2 278");
currentSystemGoal = 2;
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 7 33 53 0 2 272");
currentSystemGoal = 2;
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 7 33 53 0 2 281");
currentSystemGoal = 2;
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  inputEventCase27();
}
void inputEventCase27() {
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 7 0 2 223");
currentSystemGoal = 2;
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 7 0 2 228");
currentSystemGoal = 2;
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 7 44 0 2 241");
currentSystemGoal = 2;
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 7 0 2 233");
currentSystemGoal = 2;
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 7 0 2 238");
currentSystemGoal = 2;
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 7 44 0 2 237");
currentSystemGoal = 2;
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 7 0 2 246");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 7 0 2 266");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 7 0 2 268");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 7 0 2 272");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 7 0 2 274");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 7 0 2 278");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 7 0 2 281");
currentSystemGoal = 2;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 7 0 3 212");
currentSystemGoal = 3;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 7 0 3 212");
currentSystemGoal = 3;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 7 33 53 0 3 246");
currentSystemGoal = 3;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 7 33 53 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 7 33 53 0 3 268");
currentSystemGoal = 3;
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 7 33 53 0 3 274");
currentSystemGoal = 3;
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 7 33 44 53 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 7 33 53 0 3 278");
currentSystemGoal = 3;
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 7 33 53 0 3 272");
currentSystemGoal = 3;
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 7 33 53 0 3 281");
currentSystemGoal = 3;
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 7 0 3 223");
currentSystemGoal = 3;
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 7 0 3 228");
currentSystemGoal = 3;
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 7 44 0 3 241");
currentSystemGoal = 3;
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 7 0 3 233");
currentSystemGoal = 3;
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 7 0 3 238");
currentSystemGoal = 3;
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 7 44 0 3 237");
currentSystemGoal = 3;
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 7 0 3 246");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 7 0 3 266");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 7 0 3 268");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 7 0 3 272");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 7 0 3 274");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 7 0 3 278");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 7 0 3 281");
currentSystemGoal = 3;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 7 0 4 212");
currentSystemGoal = 4;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 7 0 4 212");
currentSystemGoal = 4;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 7 33 53 0 4 246");
currentSystemGoal = 4;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  inputEventCase28();
}
void inputEventCase28() {
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 7 33 53 0 3 266");
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 7 33 53 0 4 268");
currentSystemGoal = 4;
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 7 33 53 0 3 274");
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 7 33 44 53 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 7 33 53 0 4 278");
currentSystemGoal = 4;
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 7 33 53 0 3 272");
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 7 33 53 0 3 281");
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 7 0 3 223");
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 7 0 4 228");
currentSystemGoal = 4;
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 7 44 0 3 241");
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 7 0 3 233");
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 7 0 4 238");
currentSystemGoal = 4;
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 7 44 0 3 237");
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 7 0 4 246");
currentSystemGoal = 4;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 7 0 3 266");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 7 0 4 268");
currentSystemGoal = 4;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 7 0 3 272");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 7 0 3 274");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 7 0 4 278");
currentSystemGoal = 4;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 7 0 3 281");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 7 0 5 212");
currentSystemGoal = 5;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 7 0 5 212");
currentSystemGoal = 5;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 7 33 53 0 5 246");
currentSystemGoal = 5;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 7 33 53 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 7 33 53 0 4 268");
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 7 33 53 0 4 274");
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 7 33 44 53 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 7 33 53 0 4 278");
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 7 33 53 0 5 272");
currentSystemGoal = 5;
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 7 33 53 0 4 281");
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 7 0 5 223");
currentSystemGoal = 5;
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 7 0 4 228");
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 7 44 0 5 241");
currentSystemGoal = 5;
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  inputEventCase29();
}
void inputEventCase29() {
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 7 0 4 233");
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 7 0 4 238");
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 7 44 0 4 237");
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 7 0 5 246");
currentSystemGoal = 5;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 7 0 5 266");
currentSystemGoal = 5;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 7 0 4 268");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 7 0 5 272");
currentSystemGoal = 5;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 7 0 4 274");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 7 0 4 278");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 7 0 4 281");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 7 0 0 212");
currentSystemGoal = 0;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 7 0 0 212");
currentSystemGoal = 0;
controllerState = 212;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 7 33 53 0 0 246");
currentSystemGoal = 0;
controllerState = 246;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 7 33 53 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 7 33 53 0 0 268");
currentSystemGoal = 0;
controllerState = 268;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 7 33 53 0 0 274");
currentSystemGoal = 0;
controllerState = 274;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 7 33 44 53 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction7(); gameAction57(); gameAction68(); gameAction77();
gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 7 33 53 0 5 278");
controllerState = 278;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 7 33 53 0 5 272");
controllerState = 272;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 7 33 53 0 5 281");
controllerState = 281;
gameAction7(); gameAction57(); gameAction77(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 7 0 0 223");
currentSystemGoal = 0;
controllerState = 223;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 7 0 0 228");
currentSystemGoal = 0;
controllerState = 228;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 7 44 0 0 241");
currentSystemGoal = 0;
controllerState = 241;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 7 0 0 233");
currentSystemGoal = 0;
controllerState = 233;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 7 0 5 238");
controllerState = 238;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 7 44 0 0 237");
currentSystemGoal = 0;
controllerState = 237;
gameAction7(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 7 0 0 246");
currentSystemGoal = 0;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 7 0 0 266");
currentSystemGoal = 0;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 7 0 0 268");
currentSystemGoal = 0;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 7 0 5 272");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 7 0 0 274");
currentSystemGoal = 0;
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 7 0 5 278");
gameAction7(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 7 0 5 281");
gameAction7(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void registerNewTeam_onTerminate() {
onInputAction7();
 }
void onInputAction8() {
  logCurrentState("inputActionloadTeam.terminates",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 8 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 8 0 1 182");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 8 0 1 184");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 8 0 1 184");
currentSystemGoal = 1;
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 8 0 1 188");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 8 0 1 188");
currentSystemGoal = 1;
controllerState = 188;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 8 0 1 203");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 8 0 1 204");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 8 0 1 203");
currentSystemGoal = 1;
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 8 0 1 204");
currentSystemGoal = 1;
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 8 0 1 182");
currentSystemGoal = 1;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 8 0 1 217");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 8 44 0 1 215");
currentSystemGoal = 1;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 8 0 1 217");
currentSystemGoal = 1;
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 8 0 1 227");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 8 0 1 227");
currentSystemGoal = 1;
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 8 0 1 246");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 8 0 1 246");
currentSystemGoal = 1;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 8 0 1 268");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 8 44 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 8 0 1 268");
currentSystemGoal = 1;
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 8 0 1 278");
currentSystemGoal = 1;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 8 0 1 278");
currentSystemGoal = 1;
controllerState = 278;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 8 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 8 0 2 182");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 8 0 1 184");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 8 0 1 184");
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 8 0 1 188");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 8 0 1 188");
controllerState = 188;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 8 0 1 203");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 8 0 1 204");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 8 0 1 203");
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 8 0 1 204");
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 8 0 2 182");
currentSystemGoal = 2;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 8 0 2 217");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 8 44 0 2 215");
currentSystemGoal = 2;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 8 0 2 217");
currentSystemGoal = 2;
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 8 0 2 227");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 8 0 2 227");
currentSystemGoal = 2;
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 8 0 2 246");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 8 0 2 246");
currentSystemGoal = 2;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 8 0 2 268");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 8 44 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 8 0 2 268");
currentSystemGoal = 2;
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 8 0 2 278");
currentSystemGoal = 2;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 8 0 2 278");
currentSystemGoal = 2;
controllerState = 278;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 8 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 8 0 3 182");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 8 0 2 184");
gameAction8(); gameAction24();
return; }
  inputEventCase30();
}
void inputEventCase30() {
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 8 0 2 184");
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 8 0 2 188");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 8 0 2 188");
controllerState = 188;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 8 0 2 203");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 8 0 2 204");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 8 0 2 203");
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 8 0 2 204");
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 8 0 3 182");
currentSystemGoal = 3;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 8 0 3 217");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 8 44 0 3 215");
currentSystemGoal = 3;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 8 0 3 217");
currentSystemGoal = 3;
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 8 0 3 227");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 8 0 3 227");
currentSystemGoal = 3;
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 8 0 3 246");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 8 0 3 246");
currentSystemGoal = 3;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 8 0 3 268");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 8 44 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 8 0 3 268");
currentSystemGoal = 3;
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 8 0 3 278");
currentSystemGoal = 3;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 8 0 3 278");
currentSystemGoal = 3;
controllerState = 278;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 8 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 8 0 4 182");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 8 0 4 184");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 8 0 4 184");
currentSystemGoal = 4;
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 8 0 4 188");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 8 0 4 188");
currentSystemGoal = 4;
controllerState = 188;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 8 0 4 203");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 8 0 4 204");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 8 0 4 203");
currentSystemGoal = 4;
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 8 0 4 204");
currentSystemGoal = 4;
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 8 0 4 182");
currentSystemGoal = 4;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 8 0 4 217");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 8 44 0 4 215");
currentSystemGoal = 4;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 8 0 4 217");
currentSystemGoal = 4;
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 8 0 4 227");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 8 0 4 227");
currentSystemGoal = 4;
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 8 0 4 246");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 8 0 4 246");
currentSystemGoal = 4;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 8 0 4 268");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 8 44 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 8 0 4 268");
currentSystemGoal = 4;
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 8 0 4 278");
currentSystemGoal = 4;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 8 0 4 278");
currentSystemGoal = 4;
controllerState = 278;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 8 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 8 0 5 182");
currentSystemGoal = 5;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 8 0 5 184");
currentSystemGoal = 5;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 8 0 5 184");
currentSystemGoal = 5;
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 8 0 4 188");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 8 0 4 188");
controllerState = 188;
gameAction8(); gameAction24();
return; }
  inputEventCase31();
}
void inputEventCase31() {
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 8 0 5 203");
currentSystemGoal = 5;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 8 0 4 204");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 8 0 5 203");
currentSystemGoal = 5;
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 8 0 4 204");
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 8 0 5 182");
currentSystemGoal = 5;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 8 0 4 217");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 8 44 0 5 215");
currentSystemGoal = 5;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 8 0 4 217");
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 8 0 4 227");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 8 0 4 227");
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 8 0 5 246");
currentSystemGoal = 5;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 8 0 5 246");
currentSystemGoal = 5;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 8 0 4 268");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 8 44 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 8 0 4 268");
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 8 0 4 278");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 8 0 4 278");
controllerState = 278;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 8 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 8 0 0 182");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 8 0 0 184");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 8 0 0 184");
currentSystemGoal = 0;
controllerState = 184;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 8 0 0 188");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 8 0 0 188");
currentSystemGoal = 0;
controllerState = 188;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 8 0 5 203");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 8 0 5 204");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 8 0 5 203");
controllerState = 203;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 8 0 5 204");
controllerState = 204;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 8 0 0 182");
currentSystemGoal = 0;
controllerState = 182;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 8 0 0 217");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 8 44 0 0 215");
currentSystemGoal = 0;
controllerState = 215;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 8 0 0 217");
currentSystemGoal = 0;
controllerState = 217;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 8 0 5 227");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 8 0 5 227");
controllerState = 227;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 8 0 0 246");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 8 0 0 246");
currentSystemGoal = 0;
controllerState = 246;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 8 0 0 268");
currentSystemGoal = 0;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 8 44 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction8(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 8 0 0 268");
currentSystemGoal = 0;
controllerState = 268;
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 8 0 5 278");
gameAction8(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 8 0 5 278");
controllerState = 278;
gameAction8(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void loadTeam_onTerminate() {
onInputAction8();
 }
void onInputAction9() {
  logCurrentState("inputActionstoreData.terminates",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 9 0 1 211");
currentSystemGoal = 1;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 9 44 0 1 215");
currentSystemGoal = 1;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 9 0 1 203");
currentSystemGoal = 1;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 9 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 9 0 1 203");
currentSystemGoal = 1;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 9 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 9 0 1 203");
currentSystemGoal = 1;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 9 44 0 1 187");
currentSystemGoal = 1;
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 9 0 1 205");
currentSystemGoal = 1;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 9 0 1 205");
currentSystemGoal = 1;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 9 0 1 221");
currentSystemGoal = 1;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 9 44 0 1 215");
currentSystemGoal = 1;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 9 0 1 221");
currentSystemGoal = 1;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 9 0 1 221");
currentSystemGoal = 1;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 9 44 0 1 215");
currentSystemGoal = 1;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 9 0 1 221");
currentSystemGoal = 1;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 9 44 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 9 0 1 272");
currentSystemGoal = 1;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 9 44 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 9 0 1 272");
currentSystemGoal = 1;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 9 0 1 272");
currentSystemGoal = 1;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 9 44 0 1 266");
currentSystemGoal = 1;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 9 0 1 272");
currentSystemGoal = 1;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 9 0 2 211");
currentSystemGoal = 2;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 9 44 0 2 215");
currentSystemGoal = 2;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 9 0 1 203");
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 9 0 1 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 9 0 1 203");
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 9 0 1 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 9 0 1 203");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 9 44 0 1 187");
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 9 0 1 205");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 9 0 1 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 9 0 2 221");
currentSystemGoal = 2;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 9 44 0 2 215");
currentSystemGoal = 2;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 9 0 2 221");
currentSystemGoal = 2;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 9 0 2 221");
currentSystemGoal = 2;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 9 44 0 2 215");
currentSystemGoal = 2;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 9 0 2 221");
currentSystemGoal = 2;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 9 44 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 9 0 2 272");
currentSystemGoal = 2;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 9 44 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 9 0 2 272");
currentSystemGoal = 2;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 9 0 2 272");
currentSystemGoal = 2;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  inputEventCase32();
}
void inputEventCase32() {
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 9 44 0 2 266");
currentSystemGoal = 2;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 9 0 2 272");
currentSystemGoal = 2;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 9 0 3 211");
currentSystemGoal = 3;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 9 44 0 3 215");
currentSystemGoal = 3;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 9 0 2 203");
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 9 0 2 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 9 0 2 203");
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 9 0 2 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 9 0 2 203");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 9 44 0 2 187");
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 9 0 2 205");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 9 0 2 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 9 0 3 221");
currentSystemGoal = 3;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 9 44 0 3 215");
currentSystemGoal = 3;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 9 0 3 221");
currentSystemGoal = 3;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 9 0 3 221");
currentSystemGoal = 3;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 9 44 0 3 215");
currentSystemGoal = 3;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 9 0 3 221");
currentSystemGoal = 3;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 9 44 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 9 0 3 272");
currentSystemGoal = 3;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 9 44 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 9 0 3 272");
currentSystemGoal = 3;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 9 0 3 272");
currentSystemGoal = 3;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 9 44 0 3 266");
currentSystemGoal = 3;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 9 0 3 272");
currentSystemGoal = 3;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 9 0 4 211");
currentSystemGoal = 4;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 9 44 0 4 215");
currentSystemGoal = 4;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 9 0 4 203");
currentSystemGoal = 4;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 9 0 3 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 9 0 4 203");
currentSystemGoal = 4;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 9 0 3 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 9 0 4 203");
currentSystemGoal = 4;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 9 44 0 4 187");
currentSystemGoal = 4;
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 9 0 3 205");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 9 0 3 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 9 0 3 221");
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 9 44 0 4 215");
currentSystemGoal = 4;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 9 0 3 221");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 9 0 3 221");
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 9 44 0 4 215");
currentSystemGoal = 4;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 9 0 3 221");
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 9 44 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 9 0 3 272");
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 9 44 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  inputEventCase33();
}
void inputEventCase33() {
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 9 0 3 272");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 9 0 3 272");
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 9 44 0 4 266");
currentSystemGoal = 4;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 9 0 3 272");
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 9 0 5 211");
currentSystemGoal = 5;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 9 44 0 5 215");
currentSystemGoal = 5;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 9 0 5 203");
currentSystemGoal = 5;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 9 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 9 0 5 203");
currentSystemGoal = 5;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 9 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 9 0 5 203");
currentSystemGoal = 5;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 9 44 0 5 187");
currentSystemGoal = 5;
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 9 0 5 205");
currentSystemGoal = 5;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 9 0 5 205");
currentSystemGoal = 5;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 9 0 5 221");
currentSystemGoal = 5;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 9 44 0 5 215");
currentSystemGoal = 5;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 9 0 5 221");
currentSystemGoal = 5;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 9 0 5 221");
currentSystemGoal = 5;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 9 44 0 5 215");
currentSystemGoal = 5;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 9 0 5 221");
currentSystemGoal = 5;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 9 44 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 9 0 5 272");
currentSystemGoal = 5;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 9 44 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 9 0 5 272");
currentSystemGoal = 5;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 9 0 5 272");
currentSystemGoal = 5;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 9 44 0 5 266");
currentSystemGoal = 5;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 9 0 5 272");
currentSystemGoal = 5;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 9 0 0 211");
currentSystemGoal = 0;
controllerState = 211;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 9 44 0 0 215");
currentSystemGoal = 0;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 9 0 0 203");
currentSystemGoal = 0;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 9 0 0 205");
currentSystemGoal = 0;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 9 0 0 203");
currentSystemGoal = 0;
controllerState = 203;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 9 0 0 205");
currentSystemGoal = 0;
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 9 0 5 203");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 9 44 0 0 187");
currentSystemGoal = 0;
controllerState = 187;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 9 0 5 205");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 9 0 5 205");
controllerState = 205;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 9 0 0 221");
currentSystemGoal = 0;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 9 44 0 0 215");
currentSystemGoal = 0;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 9 0 5 221");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 9 0 0 221");
currentSystemGoal = 0;
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 9 44 0 0 215");
currentSystemGoal = 0;
controllerState = 215;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 9 0 5 221");
controllerState = 221;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 9 44 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  inputEventCase34();
}
void inputEventCase34() {
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 9 0 0 272");
currentSystemGoal = 0;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 9 44 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 9 0 5 272");
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 9 0 0 272");
currentSystemGoal = 0;
controllerState = 272;
gameAction9(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 9 44 0 0 266");
currentSystemGoal = 0;
controllerState = 266;
gameAction9(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 9 0 5 272");
controllerState = 272;
gameAction9(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void storeData_onTerminate() {
onInputAction9();
 }
void onInputAction10() {
  logCurrentState("inputActionMenuItemAddExpense",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 10 1 3 5 7 9 12 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 10 1 3 5 7 9 12 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 10 1 3 5 7 9 12 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 10 1 3 5 7 9 12 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 10 1 3 5 7 9 12 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 10 1 3 5 7 9 12 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 10 1 3 5 7 9 12 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 10 1 3 5 7 9 12 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 10 1 3 5 7 9 12 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 10 1 3 5 7 9 12 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase35();
}
void inputEventCase35() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 10 1 3 5 7 9 12 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 10 1 3 5 7 9 12 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 10 1 3 5 7 9 12 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 10 1 3 5 7 9 12 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 10 1 3 5 7 9 12 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 10 1 3 5 7 9 12 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 10 1 3 5 7 9 12 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 10 1 3 5 7 9 12 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 10 1 3 5 7 9 12 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 10 1 3 5 7 9 12 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase36();
}
void inputEventCase36() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 10 1 3 5 7 9 12 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 10 1 3 5 7 9 12 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 10 1 3 5 7 9 12 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 10 1 3 5 7 9 12 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 10 1 3 5 7 9 12 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 10 1 3 5 7 9 12 19 25 35 0 1 184");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 10 1 3 5 7 9 12 19 25 35 0 1 187");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 10 1 3 5 7 9 12 19 25 35 0 1 188");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 10 1 3 5 7 9 12 19 25 35 0 1 190");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 10 1 3 5 7 9 12 19 25 35 0 1 203");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase37();
}
void inputEventCase37() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 10 1 3 5 7 9 12 19 25 35 0 1 204");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 10 1 3 5 7 9 12 19 25 35 0 1 205");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 10 1 3 5 7 9 12 19 25 35 0 1 206");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 10 1 3 5 7 9 12 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 10 1 3 5 7 9 12 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 10 1 3 5 7 9 12 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 10 1 3 5 7 9 12 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 10 1 3 5 7 9 12 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 10 1 3 5 7 9 12 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 10 1 3 5 7 9 12 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase38();
}
void inputEventCase38() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 10 1 3 5 7 9 12 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 10 1 3 5 7 9 12 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 10 1 3 5 7 9 12 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 10 1 3 5 7 9 12 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 10 1 3 5 7 9 12 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 10 1 3 5 7 9 12 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 10 1 3 5 7 9 12 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 10 1 3 5 7 9 12 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 10 1 3 5 7 9 12 19 25 35 0 2 184");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 10 1 3 5 7 9 12 19 25 35 0 2 187");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase39();
}
void inputEventCase39() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 10 1 3 5 7 9 12 19 25 35 0 2 188");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 10 1 3 5 7 9 12 19 25 35 0 2 190");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 10 1 3 5 7 9 12 19 25 35 0 2 203");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 10 1 3 5 7 9 12 19 25 35 0 2 204");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 10 1 3 5 7 9 12 19 25 35 0 2 205");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 10 1 3 5 7 9 12 19 25 35 0 2 206");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 10 1 3 5 7 9 12 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 10 1 3 5 7 9 12 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 10 1 3 5 7 9 12 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 10 1 3 5 7 9 12 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase40();
}
void inputEventCase40() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 10 1 3 5 7 9 12 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 10 1 3 5 7 9 12 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 10 1 3 5 7 9 12 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 10 1 3 5 7 9 12 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 10 1 3 5 7 9 12 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 10 1 3 5 7 9 12 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 10 1 3 5 7 9 12 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 10 1 3 5 7 9 12 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 10 1 3 5 7 9 12 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 10 1 3 5 7 9 12 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase41();
}
void inputEventCase41() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 10 1 3 5 7 9 12 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 10 1 3 5 7 9 12 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 10 1 3 5 7 9 12 19 25 35 0 3 187");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 10 1 3 5 7 9 12 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 10 1 3 5 7 9 12 19 25 35 0 3 190");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 10 1 3 5 7 9 12 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 10 1 3 5 7 9 12 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 10 1 3 5 7 9 12 19 25 35 0 3 205");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 10 1 3 5 7 9 12 19 25 35 0 3 206");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 10 1 3 5 7 9 12 19 25 35 0 3 215");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase42();
}
void inputEventCase42() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 10 1 3 5 7 9 12 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 10 1 3 5 7 9 12 19 25 35 0 3 221");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 10 1 3 5 7 9 12 19 25 35 0 3 224");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 10 1 3 5 7 9 12 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 10 1 3 5 7 9 12 19 25 35 0 3 231");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 10 1 3 5 7 9 12 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 10 1 3 5 7 9 12 19 25 35 0 3 266");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 10 1 3 5 7 9 12 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 10 1 3 5 7 9 12 19 25 35 0 3 272");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 10 1 3 5 7 9 12 19 25 35 0 3 274");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase43();
}
void inputEventCase43() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 10 1 3 5 7 9 12 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 10 1 3 5 7 9 12 19 25 35 0 3 281");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 10 1 3 5 7 9 12 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 10 1 3 5 7 9 12 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 10 1 3 5 7 9 12 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 10 1 3 5 7 9 12 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 10 1 3 5 7 9 12 19 25 35 0 4 188");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 10 1 3 5 7 9 12 19 25 35 0 4 190");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 10 1 3 5 7 9 12 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 10 1 3 5 7 9 12 19 25 35 0 4 204");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase44();
}
void inputEventCase44() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 10 1 3 5 7 9 12 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 10 1 3 5 7 9 12 19 25 35 0 4 206");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 10 1 3 5 7 9 12 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 10 1 3 5 7 9 12 19 25 35 0 4 217");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 10 1 3 5 7 9 12 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 10 1 3 5 7 9 12 19 25 35 0 4 224");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 10 1 3 5 7 9 12 19 25 35 0 4 227");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 10 1 3 5 7 9 12 19 25 35 0 4 231");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 10 1 3 5 7 9 12 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 10 1 3 5 7 9 12 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase45();
}
void inputEventCase45() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 10 1 3 5 7 9 12 19 25 35 0 4 268");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 10 1 3 5 7 9 12 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 10 1 3 5 7 9 12 19 25 35 0 4 274");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 10 1 3 5 7 9 12 19 25 35 0 4 278");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 10 1 3 5 7 9 12 19 25 35 0 4 281");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 10 1 3 5 7 9 12 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 10 1 3 5 7 9 12 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 10 1 3 5 7 9 12 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 10 1 3 5 7 9 12 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 10 1 3 5 7 9 12 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase46();
}
void inputEventCase46() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 10 1 3 5 7 9 12 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 10 1 3 5 7 9 12 19 25 35 0 5 203");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 10 1 3 5 7 9 12 19 25 35 0 5 204");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 10 1 3 5 7 9 12 19 25 35 0 5 205");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 10 1 3 5 7 9 12 19 25 35 0 5 206");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 10 1 3 5 7 9 12 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 10 1 3 5 7 9 12 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 10 1 3 5 7 9 12 19 25 35 0 5 221");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 10 1 3 5 7 9 12 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 10 1 3 5 7 9 12 19 25 35 0 5 227");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase47();
}
void inputEventCase47() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 10 1 3 5 7 9 12 19 25 35 0 5 231");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 10 1 3 5 7 9 12 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 10 1 3 5 7 9 12 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 10 1 3 5 7 9 12 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 10 1 3 5 7 9 12 19 25 35 0 5 272");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 10 1 3 5 7 9 12 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 10 1 3 5 7 9 12 19 25 35 0 5 278");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 10 1 3 5 7 9 12 19 25 35 0 5 281");
gameAction10(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction36(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemAddExpense() {
onInputAction10();
 }
 void emit_MenuItemAddExpense() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemAddExpense();
      }});
 }
void onInputAction11() {
  logCurrentState("inputActionMenuItemExpenseList",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 11 2 3 5 7 9 11 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 11 2 3 5 7 9 11 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 11 2 3 5 7 9 11 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 11 2 3 5 7 9 11 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 11 2 3 5 7 9 11 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 11 2 3 5 7 9 11 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 11 2 3 5 7 9 11 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 11 2 3 5 7 9 11 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 11 2 3 5 7 9 11 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 11 2 3 5 7 9 11 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase48();
}
void inputEventCase48() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 11 2 3 5 7 9 11 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 11 2 3 5 7 9 11 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 11 2 3 5 7 9 11 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 11 2 3 5 7 9 11 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 11 2 3 5 7 9 11 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 11 2 3 5 7 9 11 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 11 2 3 5 7 9 11 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 11 2 3 5 7 9 11 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 11 2 3 5 7 9 11 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 11 2 3 5 7 9 11 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase49();
}
void inputEventCase49() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 11 2 3 5 7 9 11 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 11 2 3 5 7 9 11 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 11 2 3 5 7 9 11 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 11 2 3 5 7 9 11 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 11 2 3 5 7 9 11 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 11 2 3 5 7 9 11 19 25 35 0 1 184");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 11 2 3 5 7 9 11 19 25 35 0 1 187");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 11 2 3 5 7 9 11 19 25 35 0 1 188");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 11 2 3 5 7 9 11 19 25 35 0 1 190");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 11 2 3 5 7 9 11 19 25 35 0 1 203");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase50();
}
void inputEventCase50() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 11 2 3 5 7 9 11 19 25 35 0 1 204");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 11 2 3 5 7 9 11 19 25 35 0 1 205");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 11 2 3 5 7 9 11 19 25 35 0 1 206");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 11 2 3 5 7 9 11 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 11 2 3 5 7 9 11 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 11 2 3 5 7 9 11 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 11 2 3 5 7 9 11 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 11 2 3 5 7 9 11 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 11 2 3 5 7 9 11 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 11 2 3 5 7 9 11 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase51();
}
void inputEventCase51() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 11 2 3 5 7 9 11 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 11 2 3 5 7 9 11 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 11 2 3 5 7 9 11 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 11 2 3 5 7 9 11 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 11 2 3 5 7 9 11 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 11 2 3 5 7 9 11 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 11 2 3 5 7 9 11 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 11 2 3 5 7 9 11 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 11 2 3 5 7 9 11 19 25 35 0 2 184");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 11 2 3 5 7 9 11 19 25 35 0 2 187");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase52();
}
void inputEventCase52() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 11 2 3 5 7 9 11 19 25 35 0 2 188");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 11 2 3 5 7 9 11 19 25 35 0 2 190");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 11 2 3 5 7 9 11 19 25 35 0 2 203");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 11 2 3 5 7 9 11 19 25 35 0 2 204");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 11 2 3 5 7 9 11 19 25 35 0 2 205");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 11 2 3 5 7 9 11 19 25 35 0 2 206");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 11 2 3 5 7 9 11 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 11 2 3 5 7 9 11 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 11 2 3 5 7 9 11 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 11 2 3 5 7 9 11 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase53();
}
void inputEventCase53() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 11 2 3 5 7 9 11 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 11 2 3 5 7 9 11 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 11 2 3 5 7 9 11 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 11 2 3 5 7 9 11 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 11 2 3 5 7 9 11 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 11 2 3 5 7 9 11 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 11 2 3 5 7 9 11 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 11 2 3 5 7 9 11 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 11 2 3 5 7 9 11 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 11 2 3 5 7 9 11 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase54();
}
void inputEventCase54() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 11 2 3 5 7 9 11 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 11 2 3 5 7 9 11 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 11 2 3 5 7 9 11 19 25 35 0 3 187");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 11 2 3 5 7 9 11 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 11 2 3 5 7 9 11 19 25 35 0 3 190");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 11 2 3 5 7 9 11 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 11 2 3 5 7 9 11 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 11 2 3 5 7 9 11 19 25 35 0 3 205");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 11 2 3 5 7 9 11 19 25 35 0 3 206");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 11 2 3 5 7 9 11 19 25 35 0 3 215");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase55();
}
void inputEventCase55() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 11 2 3 5 7 9 11 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 11 2 3 5 7 9 11 19 25 35 0 3 221");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 11 2 3 5 7 9 11 19 25 35 0 3 224");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 11 2 3 5 7 9 11 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 11 2 3 5 7 9 11 19 25 35 0 3 231");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 11 2 3 5 7 9 11 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 11 2 3 5 7 9 11 19 25 35 0 3 266");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 11 2 3 5 7 9 11 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 11 2 3 5 7 9 11 19 25 35 0 3 272");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 11 2 3 5 7 9 11 19 25 35 0 3 274");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase56();
}
void inputEventCase56() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 11 2 3 5 7 9 11 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 11 2 3 5 7 9 11 19 25 35 0 3 281");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 11 2 3 5 7 9 11 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 11 2 3 5 7 9 11 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 11 2 3 5 7 9 11 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 11 2 3 5 7 9 11 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 11 2 3 5 7 9 11 19 25 35 0 4 188");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 11 2 3 5 7 9 11 19 25 35 0 4 190");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 11 2 3 5 7 9 11 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 11 2 3 5 7 9 11 19 25 35 0 4 204");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase57();
}
void inputEventCase57() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 11 2 3 5 7 9 11 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 11 2 3 5 7 9 11 19 25 35 0 4 206");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 11 2 3 5 7 9 11 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 11 2 3 5 7 9 11 19 25 35 0 4 217");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 11 2 3 5 7 9 11 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 11 2 3 5 7 9 11 19 25 35 0 4 224");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 11 2 3 5 7 9 11 19 25 35 0 4 227");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 11 2 3 5 7 9 11 19 25 35 0 4 231");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 11 2 3 5 7 9 11 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 11 2 3 5 7 9 11 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase58();
}
void inputEventCase58() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 11 2 3 5 7 9 11 19 25 35 0 4 268");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 11 2 3 5 7 9 11 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 11 2 3 5 7 9 11 19 25 35 0 4 274");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 11 2 3 5 7 9 11 19 25 35 0 4 278");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 11 2 3 5 7 9 11 19 25 35 0 4 281");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 11 2 3 5 7 9 11 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 11 2 3 5 7 9 11 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 11 2 3 5 7 9 11 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 11 2 3 5 7 9 11 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 11 2 3 5 7 9 11 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase59();
}
void inputEventCase59() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 11 2 3 5 7 9 11 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 11 2 3 5 7 9 11 19 25 35 0 5 203");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 11 2 3 5 7 9 11 19 25 35 0 5 204");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 11 2 3 5 7 9 11 19 25 35 0 5 205");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 11 2 3 5 7 9 11 19 25 35 0 5 206");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 11 2 3 5 7 9 11 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 11 2 3 5 7 9 11 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 11 2 3 5 7 9 11 19 25 35 0 5 221");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 11 2 3 5 7 9 11 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 11 2 3 5 7 9 11 19 25 35 0 5 227");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase60();
}
void inputEventCase60() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 11 2 3 5 7 9 11 19 25 35 0 5 231");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 11 2 3 5 7 9 11 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 11 2 3 5 7 9 11 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 11 2 3 5 7 9 11 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 11 2 3 5 7 9 11 19 25 35 0 5 272");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 11 2 3 5 7 9 11 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 11 2 3 5 7 9 11 19 25 35 0 5 278");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 11 2 3 5 7 9 11 19 25 35 0 5 281");
gameAction11(); gameAction26(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemExpenseList() {
onInputAction11();
 }
 void emit_MenuItemExpenseList() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemExpenseList();
      }});
 }
void onInputAction12() {
  logCurrentState("inputActionMenuItemNewTeam",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 12 1 3 5 7 9 11 19 26 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 12 1 3 5 7 9 11 19 26 35 0 1 182");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 12 1 3 5 7 9 11 19 26 35 0 1 184");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 12 1 3 5 7 9 11 19 26 35 0 1 187");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 12 1 3 5 7 9 11 19 26 35 0 1 188");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 12 1 3 5 7 9 11 19 26 35 0 1 190");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 12 1 3 5 7 9 11 19 26 35 0 1 203");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 12 1 3 5 7 9 11 19 26 35 0 1 204");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 12 1 3 5 7 9 11 19 26 35 0 1 205");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 12 1 3 5 7 9 11 19 26 35 0 1 206");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase61();
}
void inputEventCase61() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 12 1 3 5 7 9 11 19 26 35 0 1 215");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 12 1 3 5 7 9 11 19 26 35 0 1 217");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 12 1 3 5 7 9 11 19 26 35 0 1 221");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 12 1 3 5 7 9 11 19 26 35 0 1 224");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 12 1 3 5 7 9 11 19 26 35 0 1 227");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 12 1 3 5 7 9 11 19 26 35 0 1 231");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 12 1 3 5 7 9 11 19 26 35 0 1 246");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 12 1 3 5 7 9 11 19 26 35 0 1 266");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 12 1 3 5 7 9 11 19 26 35 0 1 268");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 12 1 3 5 7 9 11 19 26 35 0 1 272");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase62();
}
void inputEventCase62() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 12 1 3 5 7 9 11 19 26 35 0 1 274");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 12 1 3 5 7 9 11 19 26 35 0 1 278");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 12 1 3 5 7 9 11 19 26 35 0 1 281");
currentSystemGoal = 1;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 12 1 3 5 7 9 11 19 26 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 12 1 3 5 7 9 11 19 26 35 0 2 182");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 12 1 3 5 7 9 11 19 26 35 0 1 184");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 12 1 3 5 7 9 11 19 26 35 0 1 187");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 12 1 3 5 7 9 11 19 26 35 0 1 188");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 12 1 3 5 7 9 11 19 26 35 0 1 190");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 12 1 3 5 7 9 11 19 26 35 0 1 203");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase63();
}
void inputEventCase63() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 12 1 3 5 7 9 11 19 26 35 0 1 204");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 12 1 3 5 7 9 11 19 26 35 0 1 205");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 12 1 3 5 7 9 11 19 26 35 0 1 206");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 12 1 3 5 7 9 11 19 26 35 0 2 215");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 12 1 3 5 7 9 11 19 26 35 0 2 217");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 12 1 3 5 7 9 11 19 26 35 0 2 221");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 12 1 3 5 7 9 11 19 26 35 0 2 224");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 12 1 3 5 7 9 11 19 26 35 0 2 227");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 12 1 3 5 7 9 11 19 26 35 0 2 231");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 12 1 3 5 7 9 11 19 26 35 0 2 246");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase64();
}
void inputEventCase64() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 12 1 3 5 7 9 11 19 26 35 0 2 266");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 12 1 3 5 7 9 11 19 26 35 0 2 268");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 12 1 3 5 7 9 11 19 26 35 0 2 272");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 12 1 3 5 7 9 11 19 26 35 0 2 274");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 12 1 3 5 7 9 11 19 26 35 0 2 278");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 12 1 3 5 7 9 11 19 26 35 0 2 281");
currentSystemGoal = 2;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 12 1 3 5 7 9 11 19 26 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 12 1 3 5 7 9 11 19 26 35 0 3 182");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 12 1 3 5 7 9 11 19 26 35 0 2 184");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 12 1 3 5 7 9 11 19 26 35 0 2 187");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase65();
}
void inputEventCase65() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 12 1 3 5 7 9 11 19 26 35 0 2 188");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 12 1 3 5 7 9 11 19 26 35 0 2 190");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 12 1 3 5 7 9 11 19 26 35 0 2 203");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 12 1 3 5 7 9 11 19 26 35 0 2 204");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 12 1 3 5 7 9 11 19 26 35 0 2 205");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 12 1 3 5 7 9 11 19 26 35 0 2 206");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 12 1 3 5 7 9 11 19 26 35 0 3 215");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 12 1 3 5 7 9 11 19 26 35 0 3 217");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 12 1 3 5 7 9 11 19 26 35 0 3 221");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 12 1 3 5 7 9 11 19 26 35 0 3 224");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase66();
}
void inputEventCase66() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 12 1 3 5 7 9 11 19 26 35 0 3 227");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 12 1 3 5 7 9 11 19 26 35 0 3 231");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 12 1 3 5 7 9 11 19 26 35 0 3 246");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 12 1 3 5 7 9 11 19 26 35 0 3 266");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 12 1 3 5 7 9 11 19 26 35 0 3 268");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 12 1 3 5 7 9 11 19 26 35 0 3 272");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 12 1 3 5 7 9 11 19 26 35 0 3 274");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 12 1 3 5 7 9 11 19 26 35 0 3 278");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 12 1 3 5 7 9 11 19 26 35 0 3 281");
currentSystemGoal = 3;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 12 1 3 5 7 9 11 19 26 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase67();
}
void inputEventCase67() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 12 1 3 5 7 9 11 19 26 35 0 4 182");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 12 1 3 5 7 9 11 19 26 35 0 4 184");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 12 1 3 5 7 9 11 19 26 35 0 3 187");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 12 1 3 5 7 9 11 19 26 35 0 4 188");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 12 1 3 5 7 9 11 19 26 35 0 3 190");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 12 1 3 5 7 9 11 19 26 35 0 4 203");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 12 1 3 5 7 9 11 19 26 35 0 4 204");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 12 1 3 5 7 9 11 19 26 35 0 3 205");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 12 1 3 5 7 9 11 19 26 35 0 3 206");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 12 1 3 5 7 9 11 19 26 35 0 3 215");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase68();
}
void inputEventCase68() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 12 1 3 5 7 9 11 19 26 35 0 4 217");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 12 1 3 5 7 9 11 19 26 35 0 3 221");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 12 1 3 5 7 9 11 19 26 35 0 3 224");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 12 1 3 5 7 9 11 19 26 35 0 4 227");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 12 1 3 5 7 9 11 19 26 35 0 3 231");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 12 1 3 5 7 9 11 19 26 35 0 4 246");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 12 1 3 5 7 9 11 19 26 35 0 3 266");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 12 1 3 5 7 9 11 19 26 35 0 4 268");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 12 1 3 5 7 9 11 19 26 35 0 3 272");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 12 1 3 5 7 9 11 19 26 35 0 3 274");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase69();
}
void inputEventCase69() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 12 1 3 5 7 9 11 19 26 35 0 4 278");
currentSystemGoal = 4;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 12 1 3 5 7 9 11 19 26 35 0 3 281");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 12 1 3 5 7 9 11 19 26 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 12 1 3 5 7 9 11 19 26 35 0 5 182");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 12 1 3 5 7 9 11 19 26 35 0 5 184");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 12 1 3 5 7 9 11 19 26 35 0 5 187");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 12 1 3 5 7 9 11 19 26 35 0 4 188");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 12 1 3 5 7 9 11 19 26 35 0 4 190");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 12 1 3 5 7 9 11 19 26 35 0 5 203");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 12 1 3 5 7 9 11 19 26 35 0 4 204");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase70();
}
void inputEventCase70() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 12 1 3 5 7 9 11 19 26 35 0 5 205");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 12 1 3 5 7 9 11 19 26 35 0 4 206");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 12 1 3 5 7 9 11 19 26 35 0 5 215");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 12 1 3 5 7 9 11 19 26 35 0 4 217");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 12 1 3 5 7 9 11 19 26 35 0 5 221");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 12 1 3 5 7 9 11 19 26 35 0 4 224");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 12 1 3 5 7 9 11 19 26 35 0 4 227");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 12 1 3 5 7 9 11 19 26 35 0 4 231");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 12 1 3 5 7 9 11 19 26 35 0 5 246");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 12 1 3 5 7 9 11 19 26 35 0 5 266");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase71();
}
void inputEventCase71() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 12 1 3 5 7 9 11 19 26 35 0 4 268");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 12 1 3 5 7 9 11 19 26 35 0 5 272");
currentSystemGoal = 5;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 12 1 3 5 7 9 11 19 26 35 0 4 274");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 12 1 3 5 7 9 11 19 26 35 0 4 278");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 12 1 3 5 7 9 11 19 26 35 0 4 281");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 12 1 3 5 7 9 11 19 26 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 12 1 3 5 7 9 11 19 26 35 0 0 182");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 12 1 3 5 7 9 11 19 26 35 0 0 184");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 12 1 3 5 7 9 11 19 26 35 0 0 187");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 12 1 3 5 7 9 11 19 26 35 0 0 188");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase72();
}
void inputEventCase72() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 12 1 3 5 7 9 11 19 26 35 0 0 190");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 12 1 3 5 7 9 11 19 26 35 0 5 203");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 12 1 3 5 7 9 11 19 26 35 0 5 204");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 12 1 3 5 7 9 11 19 26 35 0 5 205");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 12 1 3 5 7 9 11 19 26 35 0 5 206");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 12 1 3 5 7 9 11 19 26 35 0 0 215");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 12 1 3 5 7 9 11 19 26 35 0 0 217");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 12 1 3 5 7 9 11 19 26 35 0 5 221");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 12 1 3 5 7 9 11 19 26 35 0 0 224");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 12 1 3 5 7 9 11 19 26 35 0 5 227");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  inputEventCase73();
}
void inputEventCase73() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 12 1 3 5 7 9 11 19 26 35 0 5 231");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 12 1 3 5 7 9 11 19 26 35 0 0 246");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 12 1 3 5 7 9 11 19 26 35 0 0 266");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 12 1 3 5 7 9 11 19 26 35 0 0 268");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 12 1 3 5 7 9 11 19 26 35 0 5 272");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 12 1 3 5 7 9 11 19 26 35 0 0 274");
currentSystemGoal = 0;
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 12 1 3 5 7 9 11 19 26 35 0 5 278");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 12 1 3 5 7 9 11 19 26 35 0 5 281");
gameAction12(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction50(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemNewTeam() {
onInputAction12();
 }
 void emit_MenuItemNewTeam() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemNewTeam();
      }});
 }
void onInputAction13() {
  logCurrentState("inputActionMenuItemNonApprovedExpenses",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 13 1 3 5 8 9 11 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 13 1 3 5 8 9 11 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 13 1 3 5 8 9 11 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 13 1 3 5 8 9 11 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 13 1 3 5 8 9 11 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 13 1 3 5 8 9 11 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 13 1 3 5 8 9 11 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 13 1 3 5 8 9 11 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 13 1 3 5 8 9 11 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 13 1 3 5 8 9 11 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase74();
}
void inputEventCase74() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 13 1 3 5 8 9 11 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 13 1 3 5 8 9 11 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 13 1 3 5 8 9 11 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 13 1 3 5 8 9 11 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 13 1 3 5 8 9 11 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 13 1 3 5 8 9 11 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 13 1 3 5 8 9 11 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 13 1 3 5 8 9 11 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 13 1 3 5 8 9 11 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 13 1 3 5 8 9 11 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase75();
}
void inputEventCase75() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 13 1 3 5 8 9 11 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 13 1 3 5 8 9 11 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 13 1 3 5 8 9 11 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 13 1 3 5 8 9 11 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 13 1 3 5 8 9 11 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 13 1 3 5 8 9 11 19 25 35 0 1 184");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 13 1 3 5 8 9 11 19 25 35 0 1 187");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 13 1 3 5 8 9 11 19 25 35 0 1 188");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 13 1 3 5 8 9 11 19 25 35 0 1 190");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 13 1 3 5 8 9 11 19 25 35 0 1 203");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase76();
}
void inputEventCase76() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 13 1 3 5 8 9 11 19 25 35 0 1 204");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 13 1 3 5 8 9 11 19 25 35 0 1 205");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 13 1 3 5 8 9 11 19 25 35 0 1 206");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 13 1 3 5 8 9 11 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 13 1 3 5 8 9 11 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 13 1 3 5 8 9 11 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 13 1 3 5 8 9 11 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 13 1 3 5 8 9 11 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 13 1 3 5 8 9 11 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 13 1 3 5 8 9 11 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase77();
}
void inputEventCase77() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 13 1 3 5 8 9 11 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 13 1 3 5 8 9 11 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 13 1 3 5 8 9 11 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 13 1 3 5 8 9 11 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 13 1 3 5 8 9 11 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 13 1 3 5 8 9 11 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 13 1 3 5 8 9 11 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 13 1 3 5 8 9 11 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 13 1 3 5 8 9 11 19 25 35 0 2 184");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 13 1 3 5 8 9 11 19 25 35 0 2 187");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase78();
}
void inputEventCase78() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 13 1 3 5 8 9 11 19 25 35 0 2 188");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 13 1 3 5 8 9 11 19 25 35 0 2 190");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 13 1 3 5 8 9 11 19 25 35 0 2 203");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 13 1 3 5 8 9 11 19 25 35 0 2 204");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 13 1 3 5 8 9 11 19 25 35 0 2 205");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 13 1 3 5 8 9 11 19 25 35 0 2 206");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 13 1 3 5 8 9 11 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 13 1 3 5 8 9 11 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 13 1 3 5 8 9 11 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 13 1 3 5 8 9 11 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase79();
}
void inputEventCase79() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 13 1 3 5 8 9 11 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 13 1 3 5 8 9 11 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 13 1 3 5 8 9 11 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 13 1 3 5 8 9 11 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 13 1 3 5 8 9 11 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 13 1 3 5 8 9 11 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 13 1 3 5 8 9 11 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 13 1 3 5 8 9 11 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 13 1 3 5 8 9 11 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 13 1 3 5 8 9 11 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase80();
}
void inputEventCase80() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 13 1 3 5 8 9 11 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 13 1 3 5 8 9 11 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 13 1 3 5 8 9 11 19 25 35 0 3 187");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 13 1 3 5 8 9 11 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 13 1 3 5 8 9 11 19 25 35 0 3 190");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 13 1 3 5 8 9 11 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 13 1 3 5 8 9 11 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 13 1 3 5 8 9 11 19 25 35 0 3 205");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 13 1 3 5 8 9 11 19 25 35 0 3 206");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 13 1 3 5 8 9 11 19 25 35 0 3 215");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase81();
}
void inputEventCase81() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 13 1 3 5 8 9 11 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 13 1 3 5 8 9 11 19 25 35 0 3 221");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 13 1 3 5 8 9 11 19 25 35 0 3 224");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 13 1 3 5 8 9 11 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 13 1 3 5 8 9 11 19 25 35 0 3 231");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 13 1 3 5 8 9 11 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 13 1 3 5 8 9 11 19 25 35 0 3 266");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 13 1 3 5 8 9 11 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 13 1 3 5 8 9 11 19 25 35 0 3 272");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 13 1 3 5 8 9 11 19 25 35 0 3 274");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase82();
}
void inputEventCase82() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 13 1 3 5 8 9 11 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 13 1 3 5 8 9 11 19 25 35 0 3 281");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 13 1 3 5 8 9 11 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 13 1 3 5 8 9 11 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 13 1 3 5 8 9 11 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 13 1 3 5 8 9 11 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 13 1 3 5 8 9 11 19 25 35 0 4 188");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 13 1 3 5 8 9 11 19 25 35 0 4 190");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 13 1 3 5 8 9 11 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 13 1 3 5 8 9 11 19 25 35 0 4 204");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase83();
}
void inputEventCase83() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 13 1 3 5 8 9 11 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 13 1 3 5 8 9 11 19 25 35 0 4 206");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 13 1 3 5 8 9 11 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 13 1 3 5 8 9 11 19 25 35 0 4 217");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 13 1 3 5 8 9 11 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 13 1 3 5 8 9 11 19 25 35 0 4 224");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 13 1 3 5 8 9 11 19 25 35 0 4 227");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 13 1 3 5 8 9 11 19 25 35 0 4 231");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 13 1 3 5 8 9 11 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 13 1 3 5 8 9 11 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase84();
}
void inputEventCase84() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 13 1 3 5 8 9 11 19 25 35 0 4 268");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 13 1 3 5 8 9 11 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 13 1 3 5 8 9 11 19 25 35 0 4 274");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 13 1 3 5 8 9 11 19 25 35 0 4 278");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 13 1 3 5 8 9 11 19 25 35 0 4 281");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 13 1 3 5 8 9 11 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 13 1 3 5 8 9 11 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 13 1 3 5 8 9 11 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 13 1 3 5 8 9 11 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 13 1 3 5 8 9 11 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase85();
}
void inputEventCase85() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 13 1 3 5 8 9 11 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 13 1 3 5 8 9 11 19 25 35 0 5 203");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 13 1 3 5 8 9 11 19 25 35 0 5 204");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 13 1 3 5 8 9 11 19 25 35 0 5 205");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 13 1 3 5 8 9 11 19 25 35 0 5 206");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 13 1 3 5 8 9 11 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 13 1 3 5 8 9 11 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 13 1 3 5 8 9 11 19 25 35 0 5 221");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 13 1 3 5 8 9 11 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 13 1 3 5 8 9 11 19 25 35 0 5 227");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase86();
}
void inputEventCase86() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 13 1 3 5 8 9 11 19 25 35 0 5 231");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 13 1 3 5 8 9 11 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 13 1 3 5 8 9 11 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 13 1 3 5 8 9 11 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 13 1 3 5 8 9 11 19 25 35 0 5 272");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 13 1 3 5 8 9 11 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 13 1 3 5 8 9 11 19 25 35 0 5 278");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 13 1 3 5 8 9 11 19 25 35 0 5 281");
gameAction13(); gameAction25(); gameAction27(); gameAction29();
gameAction32(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemNonApprovedExpenses() {
onInputAction13();
 }
 void emit_MenuItemNonApprovedExpenses() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemNonApprovedExpenses();
      }});
 }
void onInputAction14() {
  logCurrentState("inputActionMenuItemOfflineActions",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 14 1 4 5 7 9 11 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 14 1 4 5 7 9 11 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 14 1 4 5 7 9 11 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 14 1 4 5 7 9 11 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 14 1 4 5 7 9 11 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 14 1 4 5 7 9 11 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 14 1 4 5 7 9 11 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 14 1 4 5 7 9 11 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 14 1 4 5 7 9 11 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 14 1 4 5 7 9 11 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase87();
}
void inputEventCase87() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 14 1 4 5 7 9 11 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 14 1 4 5 7 9 11 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 14 1 4 5 7 9 11 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 14 1 4 5 7 9 11 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 14 1 4 5 7 9 11 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 14 1 4 5 7 9 11 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 14 1 4 5 7 9 11 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 14 1 4 5 7 9 11 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 14 1 4 5 7 9 11 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 14 1 4 5 7 9 11 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase88();
}
void inputEventCase88() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 14 1 4 5 7 9 11 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 14 1 4 5 7 9 11 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 14 1 4 5 7 9 11 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 14 1 4 5 7 9 11 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 14 1 4 5 7 9 11 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 14 1 4 5 7 9 11 19 25 35 0 1 184");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 14 1 4 5 7 9 11 19 25 35 0 1 187");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 14 1 4 5 7 9 11 19 25 35 0 1 188");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 14 1 4 5 7 9 11 19 25 35 0 1 190");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 14 1 4 5 7 9 11 19 25 35 0 1 203");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase89();
}
void inputEventCase89() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 14 1 4 5 7 9 11 19 25 35 0 1 204");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 14 1 4 5 7 9 11 19 25 35 0 1 205");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 14 1 4 5 7 9 11 19 25 35 0 1 206");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 14 1 4 5 7 9 11 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 14 1 4 5 7 9 11 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 14 1 4 5 7 9 11 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 14 1 4 5 7 9 11 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 14 1 4 5 7 9 11 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 14 1 4 5 7 9 11 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 14 1 4 5 7 9 11 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase90();
}
void inputEventCase90() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 14 1 4 5 7 9 11 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 14 1 4 5 7 9 11 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 14 1 4 5 7 9 11 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 14 1 4 5 7 9 11 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 14 1 4 5 7 9 11 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 14 1 4 5 7 9 11 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 14 1 4 5 7 9 11 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 14 1 4 5 7 9 11 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 14 1 4 5 7 9 11 19 25 35 0 2 184");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 14 1 4 5 7 9 11 19 25 35 0 2 187");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase91();
}
void inputEventCase91() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 14 1 4 5 7 9 11 19 25 35 0 2 188");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 14 1 4 5 7 9 11 19 25 35 0 2 190");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 14 1 4 5 7 9 11 19 25 35 0 2 203");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 14 1 4 5 7 9 11 19 25 35 0 2 204");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 14 1 4 5 7 9 11 19 25 35 0 2 205");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 14 1 4 5 7 9 11 19 25 35 0 2 206");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 14 1 4 5 7 9 11 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 14 1 4 5 7 9 11 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 14 1 4 5 7 9 11 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 14 1 4 5 7 9 11 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase92();
}
void inputEventCase92() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 14 1 4 5 7 9 11 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 14 1 4 5 7 9 11 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 14 1 4 5 7 9 11 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 14 1 4 5 7 9 11 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 14 1 4 5 7 9 11 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 14 1 4 5 7 9 11 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 14 1 4 5 7 9 11 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 14 1 4 5 7 9 11 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 14 1 4 5 7 9 11 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 14 1 4 5 7 9 11 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase93();
}
void inputEventCase93() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 14 1 4 5 7 9 11 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 14 1 4 5 7 9 11 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 14 1 4 5 7 9 11 19 25 35 0 3 187");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 14 1 4 5 7 9 11 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 14 1 4 5 7 9 11 19 25 35 0 3 190");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 14 1 4 5 7 9 11 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 14 1 4 5 7 9 11 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 14 1 4 5 7 9 11 19 25 35 0 3 205");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 14 1 4 5 7 9 11 19 25 35 0 3 206");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 14 1 4 5 7 9 11 19 25 35 0 3 215");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase94();
}
void inputEventCase94() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 14 1 4 5 7 9 11 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 14 1 4 5 7 9 11 19 25 35 0 3 221");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 14 1 4 5 7 9 11 19 25 35 0 3 224");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 14 1 4 5 7 9 11 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 14 1 4 5 7 9 11 19 25 35 0 3 231");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 14 1 4 5 7 9 11 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 14 1 4 5 7 9 11 19 25 35 0 3 266");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 14 1 4 5 7 9 11 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 14 1 4 5 7 9 11 19 25 35 0 3 272");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 14 1 4 5 7 9 11 19 25 35 0 3 274");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase95();
}
void inputEventCase95() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 14 1 4 5 7 9 11 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 14 1 4 5 7 9 11 19 25 35 0 3 281");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 14 1 4 5 7 9 11 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 14 1 4 5 7 9 11 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 14 1 4 5 7 9 11 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 14 1 4 5 7 9 11 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 14 1 4 5 7 9 11 19 25 35 0 4 188");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 14 1 4 5 7 9 11 19 25 35 0 4 190");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 14 1 4 5 7 9 11 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 14 1 4 5 7 9 11 19 25 35 0 4 204");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase96();
}
void inputEventCase96() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 14 1 4 5 7 9 11 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 14 1 4 5 7 9 11 19 25 35 0 4 206");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 14 1 4 5 7 9 11 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 14 1 4 5 7 9 11 19 25 35 0 4 217");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 14 1 4 5 7 9 11 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 14 1 4 5 7 9 11 19 25 35 0 4 224");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 14 1 4 5 7 9 11 19 25 35 0 4 227");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 14 1 4 5 7 9 11 19 25 35 0 4 231");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 14 1 4 5 7 9 11 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 14 1 4 5 7 9 11 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase97();
}
void inputEventCase97() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 14 1 4 5 7 9 11 19 25 35 0 4 268");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 14 1 4 5 7 9 11 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 14 1 4 5 7 9 11 19 25 35 0 4 274");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 14 1 4 5 7 9 11 19 25 35 0 4 278");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 14 1 4 5 7 9 11 19 25 35 0 4 281");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 14 1 4 5 7 9 11 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 14 1 4 5 7 9 11 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 14 1 4 5 7 9 11 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 14 1 4 5 7 9 11 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 14 1 4 5 7 9 11 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase98();
}
void inputEventCase98() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 14 1 4 5 7 9 11 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 14 1 4 5 7 9 11 19 25 35 0 5 203");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 14 1 4 5 7 9 11 19 25 35 0 5 204");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 14 1 4 5 7 9 11 19 25 35 0 5 205");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 14 1 4 5 7 9 11 19 25 35 0 5 206");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 14 1 4 5 7 9 11 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 14 1 4 5 7 9 11 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 14 1 4 5 7 9 11 19 25 35 0 5 221");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 14 1 4 5 7 9 11 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 14 1 4 5 7 9 11 19 25 35 0 5 227");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase99();
}
void inputEventCase99() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 14 1 4 5 7 9 11 19 25 35 0 5 231");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 14 1 4 5 7 9 11 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 14 1 4 5 7 9 11 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 14 1 4 5 7 9 11 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 14 1 4 5 7 9 11 19 25 35 0 5 272");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 14 1 4 5 7 9 11 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 14 1 4 5 7 9 11 19 25 35 0 5 278");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 14 1 4 5 7 9 11 19 25 35 0 5 281");
gameAction14(); gameAction25(); gameAction28(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemOfflineActions() {
onInputAction14();
 }
 void emit_MenuItemOfflineActions() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemOfflineActions();
      }});
 }
void onInputAction15() {
  logCurrentState("inputActionMenuItemOverview",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 15 1 3 5 7 10 11 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 15 1 3 5 7 10 11 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 15 1 3 5 7 10 11 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 15 1 3 5 7 10 11 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 15 1 3 5 7 10 11 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 15 1 3 5 7 10 11 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 15 1 3 5 7 10 11 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 15 1 3 5 7 10 11 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 15 1 3 5 7 10 11 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 15 1 3 5 7 10 11 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase100();
}
void inputEventCase100() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 15 1 3 5 7 10 11 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 15 1 3 5 7 10 11 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 15 1 3 5 7 10 11 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 15 1 3 5 7 10 11 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 15 1 3 5 7 10 11 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 15 1 3 5 7 10 11 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 15 1 3 5 7 10 11 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 15 1 3 5 7 10 11 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 15 1 3 5 7 10 11 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 15 1 3 5 7 10 11 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase101();
}
void inputEventCase101() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 15 1 3 5 7 10 11 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 15 1 3 5 7 10 11 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 15 1 3 5 7 10 11 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 15 1 3 5 7 10 11 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 15 1 3 5 7 10 11 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 15 1 3 5 7 10 11 19 25 35 0 1 184");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 15 1 3 5 7 10 11 19 25 35 0 1 187");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 15 1 3 5 7 10 11 19 25 35 0 1 188");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 15 1 3 5 7 10 11 19 25 35 0 1 190");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 15 1 3 5 7 10 11 19 25 35 0 1 203");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase102();
}
void inputEventCase102() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 15 1 3 5 7 10 11 19 25 35 0 1 204");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 15 1 3 5 7 10 11 19 25 35 0 1 205");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 15 1 3 5 7 10 11 19 25 35 0 1 206");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 15 1 3 5 7 10 11 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 15 1 3 5 7 10 11 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 15 1 3 5 7 10 11 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 15 1 3 5 7 10 11 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 15 1 3 5 7 10 11 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 15 1 3 5 7 10 11 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 15 1 3 5 7 10 11 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase103();
}
void inputEventCase103() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 15 1 3 5 7 10 11 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 15 1 3 5 7 10 11 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 15 1 3 5 7 10 11 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 15 1 3 5 7 10 11 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 15 1 3 5 7 10 11 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 15 1 3 5 7 10 11 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 15 1 3 5 7 10 11 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 15 1 3 5 7 10 11 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 15 1 3 5 7 10 11 19 25 35 0 2 184");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 15 1 3 5 7 10 11 19 25 35 0 2 187");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase104();
}
void inputEventCase104() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 15 1 3 5 7 10 11 19 25 35 0 2 188");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 15 1 3 5 7 10 11 19 25 35 0 2 190");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 15 1 3 5 7 10 11 19 25 35 0 2 203");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 15 1 3 5 7 10 11 19 25 35 0 2 204");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 15 1 3 5 7 10 11 19 25 35 0 2 205");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 15 1 3 5 7 10 11 19 25 35 0 2 206");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 15 1 3 5 7 10 11 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 15 1 3 5 7 10 11 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 15 1 3 5 7 10 11 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 15 1 3 5 7 10 11 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase105();
}
void inputEventCase105() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 15 1 3 5 7 10 11 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 15 1 3 5 7 10 11 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 15 1 3 5 7 10 11 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 15 1 3 5 7 10 11 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 15 1 3 5 7 10 11 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 15 1 3 5 7 10 11 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 15 1 3 5 7 10 11 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 15 1 3 5 7 10 11 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 15 1 3 5 7 10 11 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 15 1 3 5 7 10 11 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase106();
}
void inputEventCase106() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 15 1 3 5 7 10 11 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 15 1 3 5 7 10 11 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 15 1 3 5 7 10 11 19 25 35 0 3 187");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 15 1 3 5 7 10 11 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 15 1 3 5 7 10 11 19 25 35 0 3 190");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 15 1 3 5 7 10 11 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 15 1 3 5 7 10 11 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 15 1 3 5 7 10 11 19 25 35 0 3 205");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 15 1 3 5 7 10 11 19 25 35 0 3 206");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 15 1 3 5 7 10 11 19 25 35 0 3 215");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase107();
}
void inputEventCase107() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 15 1 3 5 7 10 11 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 15 1 3 5 7 10 11 19 25 35 0 3 221");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 15 1 3 5 7 10 11 19 25 35 0 3 224");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 15 1 3 5 7 10 11 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 15 1 3 5 7 10 11 19 25 35 0 3 231");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 15 1 3 5 7 10 11 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 15 1 3 5 7 10 11 19 25 35 0 3 266");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 15 1 3 5 7 10 11 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 15 1 3 5 7 10 11 19 25 35 0 3 272");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 15 1 3 5 7 10 11 19 25 35 0 3 274");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase108();
}
void inputEventCase108() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 15 1 3 5 7 10 11 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 15 1 3 5 7 10 11 19 25 35 0 3 281");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 15 1 3 5 7 10 11 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 15 1 3 5 7 10 11 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 15 1 3 5 7 10 11 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 15 1 3 5 7 10 11 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 15 1 3 5 7 10 11 19 25 35 0 4 188");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 15 1 3 5 7 10 11 19 25 35 0 4 190");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 15 1 3 5 7 10 11 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 15 1 3 5 7 10 11 19 25 35 0 4 204");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase109();
}
void inputEventCase109() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 15 1 3 5 7 10 11 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 15 1 3 5 7 10 11 19 25 35 0 4 206");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 15 1 3 5 7 10 11 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 15 1 3 5 7 10 11 19 25 35 0 4 217");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 15 1 3 5 7 10 11 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 15 1 3 5 7 10 11 19 25 35 0 4 224");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 15 1 3 5 7 10 11 19 25 35 0 4 227");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 15 1 3 5 7 10 11 19 25 35 0 4 231");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 15 1 3 5 7 10 11 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 15 1 3 5 7 10 11 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase110();
}
void inputEventCase110() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 15 1 3 5 7 10 11 19 25 35 0 4 268");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 15 1 3 5 7 10 11 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 15 1 3 5 7 10 11 19 25 35 0 4 274");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 15 1 3 5 7 10 11 19 25 35 0 4 278");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 15 1 3 5 7 10 11 19 25 35 0 4 281");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 15 1 3 5 7 10 11 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 15 1 3 5 7 10 11 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 15 1 3 5 7 10 11 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 15 1 3 5 7 10 11 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 15 1 3 5 7 10 11 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase111();
}
void inputEventCase111() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 15 1 3 5 7 10 11 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 15 1 3 5 7 10 11 19 25 35 0 5 203");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 15 1 3 5 7 10 11 19 25 35 0 5 204");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 15 1 3 5 7 10 11 19 25 35 0 5 205");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 15 1 3 5 7 10 11 19 25 35 0 5 206");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 15 1 3 5 7 10 11 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 15 1 3 5 7 10 11 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 15 1 3 5 7 10 11 19 25 35 0 5 221");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 15 1 3 5 7 10 11 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 15 1 3 5 7 10 11 19 25 35 0 5 227");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase112();
}
void inputEventCase112() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 15 1 3 5 7 10 11 19 25 35 0 5 231");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 15 1 3 5 7 10 11 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 15 1 3 5 7 10 11 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 15 1 3 5 7 10 11 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 15 1 3 5 7 10 11 19 25 35 0 5 272");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 15 1 3 5 7 10 11 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 15 1 3 5 7 10 11 19 25 35 0 5 278");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 15 1 3 5 7 10 11 19 25 35 0 5 281");
gameAction15(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction34(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemOverview() {
onInputAction15();
 }
 void emit_MenuItemOverview() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemOverview();
      }});
 }
void onInputAction16() {
  logCurrentState("inputActionMenuItemSelectAccount",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 16 1 3 5 7 9 11 19 25 35 36 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 16 1 3 5 7 9 11 19 25 35 36 0 1 182");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 16 1 3 5 7 9 11 19 25 35 36 0 1 184");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 16 1 3 5 7 9 11 19 25 35 36 0 1 187");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 16 1 3 5 7 9 11 19 25 35 36 0 1 188");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 16 1 3 5 7 9 11 19 25 35 36 0 1 190");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 16 1 3 5 7 9 11 19 25 35 36 0 1 203");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 16 1 3 5 7 9 11 19 25 35 36 0 1 204");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 16 1 3 5 7 9 11 19 25 35 36 0 1 205");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase113();
}
void inputEventCase113() {
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 16 1 3 5 7 9 11 19 25 35 36 0 1 206");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 16 1 3 5 7 9 11 19 25 35 36 0 1 215");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 16 1 3 5 7 9 11 19 25 35 36 0 1 217");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 16 1 3 5 7 9 11 19 25 35 36 0 1 221");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 16 1 3 5 7 9 11 19 25 35 36 0 1 224");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 16 1 3 5 7 9 11 19 25 35 36 0 1 227");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 16 1 3 5 7 9 11 19 25 35 36 0 1 231");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 16 1 3 5 7 9 11 19 25 35 36 0 1 246");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 16 1 3 5 7 9 11 19 25 35 36 0 1 266");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase114();
}
void inputEventCase114() {
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 16 1 3 5 7 9 11 19 25 35 36 0 1 268");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 16 1 3 5 7 9 11 19 25 35 36 0 1 272");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 16 1 3 5 7 9 11 19 25 35 36 0 1 274");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 16 1 3 5 7 9 11 19 25 35 36 0 1 278");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 16 1 3 5 7 9 11 19 25 35 36 0 1 281");
currentSystemGoal = 1;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 16 1 3 5 7 9 11 19 25 35 36 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 16 1 3 5 7 9 11 19 25 35 36 0 2 182");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 16 1 3 5 7 9 11 19 25 35 36 0 1 184");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 16 1 3 5 7 9 11 19 25 35 36 0 1 187");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase115();
}
void inputEventCase115() {
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 16 1 3 5 7 9 11 19 25 35 36 0 1 188");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 16 1 3 5 7 9 11 19 25 35 36 0 1 190");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 16 1 3 5 7 9 11 19 25 35 36 0 1 203");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 16 1 3 5 7 9 11 19 25 35 36 0 1 204");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 16 1 3 5 7 9 11 19 25 35 36 0 1 205");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 16 1 3 5 7 9 11 19 25 35 36 0 1 206");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 16 1 3 5 7 9 11 19 25 35 36 0 2 215");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 16 1 3 5 7 9 11 19 25 35 36 0 2 217");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 16 1 3 5 7 9 11 19 25 35 36 0 2 221");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase116();
}
void inputEventCase116() {
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 16 1 3 5 7 9 11 19 25 35 36 0 2 224");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 16 1 3 5 7 9 11 19 25 35 36 0 2 227");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 16 1 3 5 7 9 11 19 25 35 36 0 2 231");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 16 1 3 5 7 9 11 19 25 35 36 0 2 246");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 16 1 3 5 7 9 11 19 25 35 36 0 2 266");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 16 1 3 5 7 9 11 19 25 35 36 0 2 268");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 16 1 3 5 7 9 11 19 25 35 36 0 2 272");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 16 1 3 5 7 9 11 19 25 35 36 0 2 274");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 16 1 3 5 7 9 11 19 25 35 36 0 2 278");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase117();
}
void inputEventCase117() {
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 16 1 3 5 7 9 11 19 25 35 36 0 2 281");
currentSystemGoal = 2;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 16 1 3 5 7 9 11 19 25 35 36 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 16 1 3 5 7 9 11 19 25 35 36 0 3 182");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 16 1 3 5 7 9 11 19 25 35 36 0 2 184");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 16 1 3 5 7 9 11 19 25 35 36 0 2 187");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 16 1 3 5 7 9 11 19 25 35 36 0 2 188");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 16 1 3 5 7 9 11 19 25 35 36 0 2 190");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 16 1 3 5 7 9 11 19 25 35 36 0 2 203");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 16 1 3 5 7 9 11 19 25 35 36 0 2 204");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase118();
}
void inputEventCase118() {
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 16 1 3 5 7 9 11 19 25 35 36 0 2 205");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 16 1 3 5 7 9 11 19 25 35 36 0 2 206");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 16 1 3 5 7 9 11 19 25 35 36 0 3 215");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 16 1 3 5 7 9 11 19 25 35 36 0 3 217");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 16 1 3 5 7 9 11 19 25 35 36 0 3 221");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 16 1 3 5 7 9 11 19 25 35 36 0 3 224");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 16 1 3 5 7 9 11 19 25 35 36 0 3 227");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 16 1 3 5 7 9 11 19 25 35 36 0 3 231");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 16 1 3 5 7 9 11 19 25 35 36 0 3 246");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase119();
}
void inputEventCase119() {
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 16 1 3 5 7 9 11 19 25 35 36 0 3 266");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 16 1 3 5 7 9 11 19 25 35 36 0 3 268");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 16 1 3 5 7 9 11 19 25 35 36 0 3 272");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 16 1 3 5 7 9 11 19 25 35 36 0 3 274");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 16 1 3 5 7 9 11 19 25 35 36 0 3 278");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 16 1 3 5 7 9 11 19 25 35 36 0 3 281");
currentSystemGoal = 3;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 16 1 3 5 7 9 11 19 25 35 36 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 16 1 3 5 7 9 11 19 25 35 36 0 4 182");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 16 1 3 5 7 9 11 19 25 35 36 0 4 184");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase120();
}
void inputEventCase120() {
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 16 1 3 5 7 9 11 19 25 35 36 0 3 187");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 16 1 3 5 7 9 11 19 25 35 36 0 4 188");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 16 1 3 5 7 9 11 19 25 35 36 0 3 190");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 16 1 3 5 7 9 11 19 25 35 36 0 4 203");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 16 1 3 5 7 9 11 19 25 35 36 0 4 204");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 16 1 3 5 7 9 11 19 25 35 36 0 3 205");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 16 1 3 5 7 9 11 19 25 35 36 0 3 206");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 16 1 3 5 7 9 11 19 25 35 36 0 3 215");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 16 1 3 5 7 9 11 19 25 35 36 0 4 217");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase121();
}
void inputEventCase121() {
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 16 1 3 5 7 9 11 19 25 35 36 0 3 221");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 16 1 3 5 7 9 11 19 25 35 36 0 3 224");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 16 1 3 5 7 9 11 19 25 35 36 0 4 227");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 16 1 3 5 7 9 11 19 25 35 36 0 3 231");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 16 1 3 5 7 9 11 19 25 35 36 0 4 246");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 16 1 3 5 7 9 11 19 25 35 36 0 3 266");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 16 1 3 5 7 9 11 19 25 35 36 0 4 268");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 16 1 3 5 7 9 11 19 25 35 36 0 3 272");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 16 1 3 5 7 9 11 19 25 35 36 0 3 274");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase122();
}
void inputEventCase122() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 16 1 3 5 7 9 11 19 25 35 36 0 4 278");
currentSystemGoal = 4;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 16 1 3 5 7 9 11 19 25 35 36 0 3 281");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 16 1 3 5 7 9 11 19 25 35 36 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 16 1 3 5 7 9 11 19 25 35 36 0 5 182");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 16 1 3 5 7 9 11 19 25 35 36 0 5 184");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 16 1 3 5 7 9 11 19 25 35 36 0 5 187");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 16 1 3 5 7 9 11 19 25 35 36 0 4 188");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 16 1 3 5 7 9 11 19 25 35 36 0 4 190");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 16 1 3 5 7 9 11 19 25 35 36 0 5 203");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase123();
}
void inputEventCase123() {
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 16 1 3 5 7 9 11 19 25 35 36 0 4 204");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 16 1 3 5 7 9 11 19 25 35 36 0 5 205");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 16 1 3 5 7 9 11 19 25 35 36 0 4 206");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 16 1 3 5 7 9 11 19 25 35 36 0 5 215");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 16 1 3 5 7 9 11 19 25 35 36 0 4 217");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 16 1 3 5 7 9 11 19 25 35 36 0 5 221");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 16 1 3 5 7 9 11 19 25 35 36 0 4 224");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 16 1 3 5 7 9 11 19 25 35 36 0 4 227");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 16 1 3 5 7 9 11 19 25 35 36 0 4 231");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase124();
}
void inputEventCase124() {
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 16 1 3 5 7 9 11 19 25 35 36 0 5 246");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 16 1 3 5 7 9 11 19 25 35 36 0 5 266");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 16 1 3 5 7 9 11 19 25 35 36 0 4 268");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 16 1 3 5 7 9 11 19 25 35 36 0 5 272");
currentSystemGoal = 5;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 16 1 3 5 7 9 11 19 25 35 36 0 4 274");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 16 1 3 5 7 9 11 19 25 35 36 0 4 278");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 16 1 3 5 7 9 11 19 25 35 36 0 4 281");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 16 1 3 5 7 9 11 19 25 35 36 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 16 1 3 5 7 9 11 19 25 35 36 0 0 182");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase125();
}
void inputEventCase125() {
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 16 1 3 5 7 9 11 19 25 35 36 0 0 184");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 16 1 3 5 7 9 11 19 25 35 36 0 0 187");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 16 1 3 5 7 9 11 19 25 35 36 0 0 188");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 16 1 3 5 7 9 11 19 25 35 36 0 0 190");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 16 1 3 5 7 9 11 19 25 35 36 0 5 203");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 16 1 3 5 7 9 11 19 25 35 36 0 5 204");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 16 1 3 5 7 9 11 19 25 35 36 0 5 205");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 16 1 3 5 7 9 11 19 25 35 36 0 5 206");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 16 1 3 5 7 9 11 19 25 35 36 0 0 215");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase126();
}
void inputEventCase126() {
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 16 1 3 5 7 9 11 19 25 35 36 0 0 217");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 16 1 3 5 7 9 11 19 25 35 36 0 5 221");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 16 1 3 5 7 9 11 19 25 35 36 0 0 224");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 16 1 3 5 7 9 11 19 25 35 36 0 5 227");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 16 1 3 5 7 9 11 19 25 35 36 0 5 231");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 16 1 3 5 7 9 11 19 25 35 36 0 0 246");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 16 1 3 5 7 9 11 19 25 35 36 0 0 266");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 16 1 3 5 7 9 11 19 25 35 36 0 0 268");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 16 1 3 5 7 9 11 19 25 35 36 0 5 272");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  inputEventCase127();
}
void inputEventCase127() {
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 16 1 3 5 7 9 11 19 25 35 36 0 0 274");
currentSystemGoal = 0;
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 16 1 3 5 7 9 11 19 25 35 36 0 5 278");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 16 1 3 5 7 9 11 19 25 35 36 0 5 281");
gameAction16(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction60(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemSelectAccount() {
onInputAction16();
 }
 void emit_MenuItemSelectAccount() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemSelectAccount();
      }});
 }
void onInputAction17() {
  logCurrentState("inputActionMenuItemTeamAdministration",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 17 1 3 6 7 9 11 19 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 17 1 3 6 7 9 11 19 25 35 0 1 182");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 17 1 3 6 7 9 11 19 25 35 0 1 184");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 17 1 3 6 7 9 11 19 25 35 0 1 187");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 17 1 3 6 7 9 11 19 25 35 0 1 188");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 17 1 3 6 7 9 11 19 25 35 0 1 190");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 17 1 3 6 7 9 11 19 25 35 0 1 203");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 17 1 3 6 7 9 11 19 25 35 0 1 204");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 17 1 3 6 7 9 11 19 25 35 0 1 205");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 17 1 3 6 7 9 11 19 25 35 0 1 206");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase128();
}
void inputEventCase128() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 17 1 3 6 7 9 11 19 25 35 0 1 215");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 17 1 3 6 7 9 11 19 25 35 0 1 217");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 17 1 3 6 7 9 11 19 25 35 0 1 221");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 17 1 3 6 7 9 11 19 25 35 0 1 224");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 17 1 3 6 7 9 11 19 25 35 0 1 227");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 17 1 3 6 7 9 11 19 25 35 0 1 231");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 17 1 3 6 7 9 11 19 25 35 0 1 246");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 17 1 3 6 7 9 11 19 25 35 0 1 266");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 17 1 3 6 7 9 11 19 25 35 0 1 268");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 17 1 3 6 7 9 11 19 25 35 0 1 272");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase129();
}
void inputEventCase129() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 17 1 3 6 7 9 11 19 25 35 0 1 274");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 17 1 3 6 7 9 11 19 25 35 0 1 278");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 17 1 3 6 7 9 11 19 25 35 0 1 281");
currentSystemGoal = 1;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 17 1 3 6 7 9 11 19 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 17 1 3 6 7 9 11 19 25 35 0 2 182");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 17 1 3 6 7 9 11 19 25 35 0 1 184");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 17 1 3 6 7 9 11 19 25 35 0 1 187");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 17 1 3 6 7 9 11 19 25 35 0 1 188");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 17 1 3 6 7 9 11 19 25 35 0 1 190");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 17 1 3 6 7 9 11 19 25 35 0 1 203");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase130();
}
void inputEventCase130() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 17 1 3 6 7 9 11 19 25 35 0 1 204");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 17 1 3 6 7 9 11 19 25 35 0 1 205");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 17 1 3 6 7 9 11 19 25 35 0 1 206");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 17 1 3 6 7 9 11 19 25 35 0 2 215");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 17 1 3 6 7 9 11 19 25 35 0 2 217");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 17 1 3 6 7 9 11 19 25 35 0 2 221");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 17 1 3 6 7 9 11 19 25 35 0 2 224");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 17 1 3 6 7 9 11 19 25 35 0 2 227");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 17 1 3 6 7 9 11 19 25 35 0 2 231");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 17 1 3 6 7 9 11 19 25 35 0 2 246");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase131();
}
void inputEventCase131() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 17 1 3 6 7 9 11 19 25 35 0 2 266");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 17 1 3 6 7 9 11 19 25 35 0 2 268");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 17 1 3 6 7 9 11 19 25 35 0 2 272");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 17 1 3 6 7 9 11 19 25 35 0 2 274");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 17 1 3 6 7 9 11 19 25 35 0 2 278");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 17 1 3 6 7 9 11 19 25 35 0 2 281");
currentSystemGoal = 2;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 17 1 3 6 7 9 11 19 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 17 1 3 6 7 9 11 19 25 35 0 3 182");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 17 1 3 6 7 9 11 19 25 35 0 2 184");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 17 1 3 6 7 9 11 19 25 35 0 2 187");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase132();
}
void inputEventCase132() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 17 1 3 6 7 9 11 19 25 35 0 2 188");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 17 1 3 6 7 9 11 19 25 35 0 2 190");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 17 1 3 6 7 9 11 19 25 35 0 2 203");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 17 1 3 6 7 9 11 19 25 35 0 2 204");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 17 1 3 6 7 9 11 19 25 35 0 2 205");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 17 1 3 6 7 9 11 19 25 35 0 2 206");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 17 1 3 6 7 9 11 19 25 35 0 3 215");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 17 1 3 6 7 9 11 19 25 35 0 3 217");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 17 1 3 6 7 9 11 19 25 35 0 3 221");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 17 1 3 6 7 9 11 19 25 35 0 3 224");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase133();
}
void inputEventCase133() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 17 1 3 6 7 9 11 19 25 35 0 3 227");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 17 1 3 6 7 9 11 19 25 35 0 3 231");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 17 1 3 6 7 9 11 19 25 35 0 3 246");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 17 1 3 6 7 9 11 19 25 35 0 3 266");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 17 1 3 6 7 9 11 19 25 35 0 3 268");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 17 1 3 6 7 9 11 19 25 35 0 3 272");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 17 1 3 6 7 9 11 19 25 35 0 3 274");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 17 1 3 6 7 9 11 19 25 35 0 3 278");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 17 1 3 6 7 9 11 19 25 35 0 3 281");
currentSystemGoal = 3;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 17 1 3 6 7 9 11 19 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase134();
}
void inputEventCase134() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 17 1 3 6 7 9 11 19 25 35 0 4 182");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 17 1 3 6 7 9 11 19 25 35 0 4 184");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 17 1 3 6 7 9 11 19 25 35 0 3 187");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 17 1 3 6 7 9 11 19 25 35 0 4 188");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 17 1 3 6 7 9 11 19 25 35 0 3 190");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 17 1 3 6 7 9 11 19 25 35 0 4 203");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 17 1 3 6 7 9 11 19 25 35 0 4 204");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 17 1 3 6 7 9 11 19 25 35 0 3 205");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 17 1 3 6 7 9 11 19 25 35 0 3 206");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 17 1 3 6 7 9 11 19 25 35 0 3 215");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase135();
}
void inputEventCase135() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 17 1 3 6 7 9 11 19 25 35 0 4 217");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 17 1 3 6 7 9 11 19 25 35 0 3 221");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 17 1 3 6 7 9 11 19 25 35 0 3 224");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 17 1 3 6 7 9 11 19 25 35 0 4 227");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 17 1 3 6 7 9 11 19 25 35 0 3 231");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 17 1 3 6 7 9 11 19 25 35 0 4 246");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 17 1 3 6 7 9 11 19 25 35 0 3 266");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 17 1 3 6 7 9 11 19 25 35 0 4 268");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 17 1 3 6 7 9 11 19 25 35 0 3 272");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 17 1 3 6 7 9 11 19 25 35 0 3 274");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase136();
}
void inputEventCase136() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 17 1 3 6 7 9 11 19 25 35 0 4 278");
currentSystemGoal = 4;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 17 1 3 6 7 9 11 19 25 35 0 3 281");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 17 1 3 6 7 9 11 19 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 17 1 3 6 7 9 11 19 25 35 0 5 182");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 17 1 3 6 7 9 11 19 25 35 0 5 184");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 17 1 3 6 7 9 11 19 25 35 0 5 187");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 17 1 3 6 7 9 11 19 25 35 0 4 188");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 17 1 3 6 7 9 11 19 25 35 0 4 190");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 17 1 3 6 7 9 11 19 25 35 0 5 203");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 17 1 3 6 7 9 11 19 25 35 0 4 204");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase137();
}
void inputEventCase137() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 17 1 3 6 7 9 11 19 25 35 0 5 205");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 17 1 3 6 7 9 11 19 25 35 0 4 206");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 17 1 3 6 7 9 11 19 25 35 0 5 215");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 17 1 3 6 7 9 11 19 25 35 0 4 217");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 17 1 3 6 7 9 11 19 25 35 0 5 221");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 17 1 3 6 7 9 11 19 25 35 0 4 224");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 17 1 3 6 7 9 11 19 25 35 0 4 227");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 17 1 3 6 7 9 11 19 25 35 0 4 231");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 17 1 3 6 7 9 11 19 25 35 0 5 246");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 17 1 3 6 7 9 11 19 25 35 0 5 266");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase138();
}
void inputEventCase138() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 17 1 3 6 7 9 11 19 25 35 0 4 268");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 17 1 3 6 7 9 11 19 25 35 0 5 272");
currentSystemGoal = 5;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 17 1 3 6 7 9 11 19 25 35 0 4 274");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 17 1 3 6 7 9 11 19 25 35 0 4 278");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 17 1 3 6 7 9 11 19 25 35 0 4 281");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 17 1 3 6 7 9 11 19 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 17 1 3 6 7 9 11 19 25 35 0 0 182");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 17 1 3 6 7 9 11 19 25 35 0 0 184");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 17 1 3 6 7 9 11 19 25 35 0 0 187");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 17 1 3 6 7 9 11 19 25 35 0 0 188");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase139();
}
void inputEventCase139() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 17 1 3 6 7 9 11 19 25 35 0 0 190");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 17 1 3 6 7 9 11 19 25 35 0 5 203");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 17 1 3 6 7 9 11 19 25 35 0 5 204");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 17 1 3 6 7 9 11 19 25 35 0 5 205");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 17 1 3 6 7 9 11 19 25 35 0 5 206");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 17 1 3 6 7 9 11 19 25 35 0 0 215");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 17 1 3 6 7 9 11 19 25 35 0 0 217");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 17 1 3 6 7 9 11 19 25 35 0 5 221");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 17 1 3 6 7 9 11 19 25 35 0 0 224");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 17 1 3 6 7 9 11 19 25 35 0 5 227");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase140();
}
void inputEventCase140() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 17 1 3 6 7 9 11 19 25 35 0 5 231");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 17 1 3 6 7 9 11 19 25 35 0 0 246");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 17 1 3 6 7 9 11 19 25 35 0 0 266");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 17 1 3 6 7 9 11 19 25 35 0 0 268");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 17 1 3 6 7 9 11 19 25 35 0 5 272");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 17 1 3 6 7 9 11 19 25 35 0 0 274");
currentSystemGoal = 0;
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 17 1 3 6 7 9 11 19 25 35 0 5 278");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 17 1 3 6 7 9 11 19 25 35 0 5 281");
gameAction17(); gameAction25(); gameAction27(); gameAction30();
gameAction31(); gameAction33(); gameAction35(); gameAction43();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemTeamAdministration() {
onInputAction17();
 }
 void emit_MenuItemTeamAdministration() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemTeamAdministration();
      }});
 }
void onInputAction18() {
  logCurrentState("inputActionMenuItemTrackReimbursement",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 18 1 3 5 7 9 11 20 25 35 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 18 1 3 5 7 9 11 20 25 35 0 1 182");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 18 1 3 5 7 9 11 20 25 35 0 1 184");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 18 1 3 5 7 9 11 20 25 35 0 1 187");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 18 1 3 5 7 9 11 20 25 35 0 1 188");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 18 1 3 5 7 9 11 20 25 35 0 1 190");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 18 1 3 5 7 9 11 20 25 35 0 1 203");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 18 1 3 5 7 9 11 20 25 35 0 1 204");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 18 1 3 5 7 9 11 20 25 35 0 1 205");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 18 1 3 5 7 9 11 20 25 35 0 1 206");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase141();
}
void inputEventCase141() {
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 18 1 3 5 7 9 11 20 25 35 0 1 215");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 18 1 3 5 7 9 11 20 25 35 0 1 217");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 18 1 3 5 7 9 11 20 25 35 0 1 221");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 18 1 3 5 7 9 11 20 25 35 0 1 224");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 18 1 3 5 7 9 11 20 25 35 0 1 227");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 18 1 3 5 7 9 11 20 25 35 0 1 231");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 18 1 3 5 7 9 11 20 25 35 0 1 246");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 18 1 3 5 7 9 11 20 25 35 0 1 266");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 18 1 3 5 7 9 11 20 25 35 0 1 268");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 18 1 3 5 7 9 11 20 25 35 0 1 272");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase142();
}
void inputEventCase142() {
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 18 1 3 5 7 9 11 20 25 35 0 1 274");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 18 1 3 5 7 9 11 20 25 35 0 1 278");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 18 1 3 5 7 9 11 20 25 35 0 1 281");
currentSystemGoal = 1;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 18 1 3 5 7 9 11 20 25 35 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 18 1 3 5 7 9 11 20 25 35 0 2 182");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 18 1 3 5 7 9 11 20 25 35 0 1 184");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 18 1 3 5 7 9 11 20 25 35 0 1 187");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 18 1 3 5 7 9 11 20 25 35 0 1 188");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 18 1 3 5 7 9 11 20 25 35 0 1 190");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 18 1 3 5 7 9 11 20 25 35 0 1 203");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase143();
}
void inputEventCase143() {
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 18 1 3 5 7 9 11 20 25 35 0 1 204");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 18 1 3 5 7 9 11 20 25 35 0 1 205");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 18 1 3 5 7 9 11 20 25 35 0 1 206");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 18 1 3 5 7 9 11 20 25 35 0 2 215");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 18 1 3 5 7 9 11 20 25 35 0 2 217");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 18 1 3 5 7 9 11 20 25 35 0 2 221");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 18 1 3 5 7 9 11 20 25 35 0 2 224");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 18 1 3 5 7 9 11 20 25 35 0 2 227");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 18 1 3 5 7 9 11 20 25 35 0 2 231");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 18 1 3 5 7 9 11 20 25 35 0 2 246");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase144();
}
void inputEventCase144() {
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 18 1 3 5 7 9 11 20 25 35 0 2 266");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 18 1 3 5 7 9 11 20 25 35 0 2 268");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 18 1 3 5 7 9 11 20 25 35 0 2 272");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 18 1 3 5 7 9 11 20 25 35 0 2 274");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 18 1 3 5 7 9 11 20 25 35 0 2 278");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 18 1 3 5 7 9 11 20 25 35 0 2 281");
currentSystemGoal = 2;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 18 1 3 5 7 9 11 20 25 35 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 18 1 3 5 7 9 11 20 25 35 0 3 182");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 18 1 3 5 7 9 11 20 25 35 0 2 184");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 18 1 3 5 7 9 11 20 25 35 0 2 187");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase145();
}
void inputEventCase145() {
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 18 1 3 5 7 9 11 20 25 35 0 2 188");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 18 1 3 5 7 9 11 20 25 35 0 2 190");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 18 1 3 5 7 9 11 20 25 35 0 2 203");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 18 1 3 5 7 9 11 20 25 35 0 2 204");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 18 1 3 5 7 9 11 20 25 35 0 2 205");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 18 1 3 5 7 9 11 20 25 35 0 2 206");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 18 1 3 5 7 9 11 20 25 35 0 3 215");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 18 1 3 5 7 9 11 20 25 35 0 3 217");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 18 1 3 5 7 9 11 20 25 35 0 3 221");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 18 1 3 5 7 9 11 20 25 35 0 3 224");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase146();
}
void inputEventCase146() {
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 18 1 3 5 7 9 11 20 25 35 0 3 227");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 18 1 3 5 7 9 11 20 25 35 0 3 231");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 18 1 3 5 7 9 11 20 25 35 0 3 246");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 18 1 3 5 7 9 11 20 25 35 0 3 266");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 18 1 3 5 7 9 11 20 25 35 0 3 268");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 18 1 3 5 7 9 11 20 25 35 0 3 272");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 18 1 3 5 7 9 11 20 25 35 0 3 274");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 18 1 3 5 7 9 11 20 25 35 0 3 278");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 18 1 3 5 7 9 11 20 25 35 0 3 281");
currentSystemGoal = 3;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 18 1 3 5 7 9 11 20 25 35 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase147();
}
void inputEventCase147() {
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 18 1 3 5 7 9 11 20 25 35 0 4 182");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 18 1 3 5 7 9 11 20 25 35 0 4 184");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 18 1 3 5 7 9 11 20 25 35 0 3 187");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 18 1 3 5 7 9 11 20 25 35 0 4 188");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 18 1 3 5 7 9 11 20 25 35 0 3 190");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 18 1 3 5 7 9 11 20 25 35 0 4 203");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 18 1 3 5 7 9 11 20 25 35 0 4 204");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 18 1 3 5 7 9 11 20 25 35 0 3 205");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 18 1 3 5 7 9 11 20 25 35 0 3 206");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 18 1 3 5 7 9 11 20 25 35 0 3 215");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase148();
}
void inputEventCase148() {
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 18 1 3 5 7 9 11 20 25 35 0 4 217");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 18 1 3 5 7 9 11 20 25 35 0 3 221");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 18 1 3 5 7 9 11 20 25 35 0 3 224");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 18 1 3 5 7 9 11 20 25 35 0 4 227");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 18 1 3 5 7 9 11 20 25 35 0 3 231");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 18 1 3 5 7 9 11 20 25 35 0 4 246");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 18 1 3 5 7 9 11 20 25 35 0 3 266");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 18 1 3 5 7 9 11 20 25 35 0 4 268");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 18 1 3 5 7 9 11 20 25 35 0 3 272");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 18 1 3 5 7 9 11 20 25 35 0 3 274");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase149();
}
void inputEventCase149() {
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 18 1 3 5 7 9 11 20 25 35 0 4 278");
currentSystemGoal = 4;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 18 1 3 5 7 9 11 20 25 35 0 3 281");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 18 1 3 5 7 9 11 20 25 35 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 18 1 3 5 7 9 11 20 25 35 0 5 182");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 18 1 3 5 7 9 11 20 25 35 0 5 184");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 18 1 3 5 7 9 11 20 25 35 0 5 187");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 18 1 3 5 7 9 11 20 25 35 0 4 188");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 18 1 3 5 7 9 11 20 25 35 0 4 190");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 18 1 3 5 7 9 11 20 25 35 0 5 203");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 18 1 3 5 7 9 11 20 25 35 0 4 204");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase150();
}
void inputEventCase150() {
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 18 1 3 5 7 9 11 20 25 35 0 5 205");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 18 1 3 5 7 9 11 20 25 35 0 4 206");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 18 1 3 5 7 9 11 20 25 35 0 5 215");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 18 1 3 5 7 9 11 20 25 35 0 4 217");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 18 1 3 5 7 9 11 20 25 35 0 5 221");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 18 1 3 5 7 9 11 20 25 35 0 4 224");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 18 1 3 5 7 9 11 20 25 35 0 4 227");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 18 1 3 5 7 9 11 20 25 35 0 4 231");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 18 1 3 5 7 9 11 20 25 35 0 5 246");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 18 1 3 5 7 9 11 20 25 35 0 5 266");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase151();
}
void inputEventCase151() {
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 18 1 3 5 7 9 11 20 25 35 0 4 268");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 18 1 3 5 7 9 11 20 25 35 0 5 272");
currentSystemGoal = 5;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 18 1 3 5 7 9 11 20 25 35 0 4 274");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 18 1 3 5 7 9 11 20 25 35 0 4 278");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 18 1 3 5 7 9 11 20 25 35 0 4 281");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 18 1 3 5 7 9 11 20 25 35 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 18 1 3 5 7 9 11 20 25 35 0 0 182");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 18 1 3 5 7 9 11 20 25 35 0 0 184");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 18 1 3 5 7 9 11 20 25 35 0 0 187");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 18 1 3 5 7 9 11 20 25 35 0 0 188");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase152();
}
void inputEventCase152() {
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 18 1 3 5 7 9 11 20 25 35 0 0 190");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 18 1 3 5 7 9 11 20 25 35 0 5 203");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 18 1 3 5 7 9 11 20 25 35 0 5 204");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 18 1 3 5 7 9 11 20 25 35 0 5 205");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 18 1 3 5 7 9 11 20 25 35 0 5 206");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 18 1 3 5 7 9 11 20 25 35 0 0 215");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 18 1 3 5 7 9 11 20 25 35 0 0 217");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 18 1 3 5 7 9 11 20 25 35 0 5 221");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 18 1 3 5 7 9 11 20 25 35 0 0 224");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 18 1 3 5 7 9 11 20 25 35 0 5 227");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  inputEventCase153();
}
void inputEventCase153() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 18 1 3 5 7 9 11 20 25 35 0 5 231");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 18 1 3 5 7 9 11 20 25 35 0 0 246");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 18 1 3 5 7 9 11 20 25 35 0 0 266");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 18 1 3 5 7 9 11 20 25 35 0 0 268");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 18 1 3 5 7 9 11 20 25 35 0 5 272");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 18 1 3 5 7 9 11 20 25 35 0 0 274");
currentSystemGoal = 0;
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 18 1 3 5 7 9 11 20 25 35 0 5 278");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 18 1 3 5 7 9 11 20 25 35 0 5 281");
gameAction18(); gameAction25(); gameAction27(); gameAction29();
gameAction31(); gameAction33(); gameAction35(); gameAction44();
gameAction49(); gameAction59(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_MenuItemTrackReimbursement() {
onInputAction18();
 }
 void emit_MenuItemTrackReimbursement() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_MenuItemTrackReimbursement();
      }});
 }
void onInputAction19() {
  logCurrentState("inputActionnewTeamSuccessfullyCreated",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 19 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 19 0 1 182");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 19 0 1 184");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 19 0 1 187");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 19 0 1 188");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 19 0 1 190");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 19 0 1 203");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 19 0 1 204");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 19 0 1 205");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 19 0 1 206");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 19 0 1 215");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 19 0 1 217");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 19 0 1 221");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 19 0 1 224");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 19 0 1 227");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 19 0 1 231");
currentSystemGoal = 1;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 19 0 1 245");
currentSystemGoal = 1;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 19 0 1 270");
currentSystemGoal = 1;
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 19 0 1 276");
currentSystemGoal = 1;
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 19 44 0 1 286");
currentSystemGoal = 1;
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 19 0 1 279");
currentSystemGoal = 1;
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 19 0 1 284");
currentSystemGoal = 1;
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 19 44 0 1 283");
currentSystemGoal = 1;
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 19 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 19 0 2 182");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 19 0 1 184");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 19 0 1 187");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 19 0 1 188");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 19 0 1 190");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 19 0 1 203");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 19 0 1 204");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 19 0 1 205");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 19 0 1 206");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 19 0 2 215");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 19 0 2 217");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 19 0 2 221");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 19 0 2 224");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 19 0 2 227");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 19 0 2 231");
currentSystemGoal = 2;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 19 0 2 245");
currentSystemGoal = 2;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 19 0 2 270");
currentSystemGoal = 2;
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 19 0 2 276");
currentSystemGoal = 2;
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 19 44 0 2 286");
currentSystemGoal = 2;
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 19 0 2 279");
currentSystemGoal = 2;
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 19 0 2 284");
currentSystemGoal = 2;
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 19 44 0 2 283");
currentSystemGoal = 2;
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 19 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 19 0 3 182");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 19 0 2 184");
gameAction19(); gameAction24();
return; }
  inputEventCase154();
}
void inputEventCase154() {
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 19 0 2 187");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 19 0 2 188");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 19 0 2 190");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 19 0 2 203");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 19 0 2 204");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 19 0 2 205");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 19 0 2 206");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 19 0 3 215");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 19 0 3 217");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 19 0 3 221");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 19 0 3 224");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 19 0 3 227");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 19 0 3 231");
currentSystemGoal = 3;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 19 0 3 245");
currentSystemGoal = 3;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 19 0 3 270");
currentSystemGoal = 3;
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 19 0 3 276");
currentSystemGoal = 3;
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 19 44 0 3 286");
currentSystemGoal = 3;
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 19 0 3 279");
currentSystemGoal = 3;
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 19 0 3 284");
currentSystemGoal = 3;
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 19 44 0 3 283");
currentSystemGoal = 3;
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 19 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 19 0 4 182");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 19 0 4 184");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 19 0 3 187");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 19 0 4 188");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 19 0 3 190");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 19 0 4 203");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 19 0 4 204");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 19 0 3 205");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 19 0 3 206");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 19 0 3 215");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 19 0 4 217");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 19 0 3 221");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 19 0 3 224");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 19 0 4 227");
currentSystemGoal = 4;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 19 0 3 231");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 19 0 4 245");
currentSystemGoal = 4;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 19 0 3 270");
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 19 0 4 276");
currentSystemGoal = 4;
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 19 44 0 3 286");
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 19 0 3 279");
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 19 0 4 284");
currentSystemGoal = 4;
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 19 44 0 3 283");
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 19 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 19 0 5 182");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 19 0 5 184");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 19 0 5 187");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 19 0 4 188");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 19 0 4 190");
gameAction19(); gameAction24();
return; }
  inputEventCase155();
}
void inputEventCase155() {
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 19 0 5 203");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 19 0 4 204");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 19 0 5 205");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 19 0 4 206");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 19 0 5 215");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 19 0 4 217");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 19 0 5 221");
currentSystemGoal = 5;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 19 0 4 224");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 19 0 4 227");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 19 0 4 231");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 19 0 5 245");
currentSystemGoal = 5;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 19 0 5 270");
currentSystemGoal = 5;
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 19 0 4 276");
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 19 44 0 5 286");
currentSystemGoal = 5;
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 19 0 4 279");
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 19 0 4 284");
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 19 44 0 4 283");
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 19 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 19 0 0 182");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 19 0 0 184");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 19 0 0 187");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 19 0 0 188");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 19 0 0 190");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 19 0 5 203");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 19 0 5 204");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 19 0 5 205");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 19 0 5 206");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 19 0 0 215");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 19 0 0 217");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 19 0 5 221");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 19 0 0 224");
currentSystemGoal = 0;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 19 0 5 227");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 19 0 5 231");
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 19 0 0 245");
currentSystemGoal = 0;
controllerState = 245;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 19 0 0 270");
currentSystemGoal = 0;
controllerState = 270;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 19 0 0 276");
currentSystemGoal = 0;
controllerState = 276;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 19 44 0 0 286");
currentSystemGoal = 0;
controllerState = 286;
gameAction19(); gameAction68(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 19 0 0 279");
currentSystemGoal = 0;
controllerState = 279;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 19 0 5 284");
controllerState = 284;
gameAction19(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 19 44 0 0 283");
currentSystemGoal = 0;
controllerState = 283;
gameAction19(); gameAction68(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_newTeamSuccessfullyCreated() {
onInputAction19();
 }
 void emit_newTeamSuccessfullyCreated() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_newTeamSuccessfullyCreated();
      }});
 }
void onInputAction20() {
  logCurrentState("inputActionnewTeamIdentifierIsFine",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 20 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 20 0 1 182");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 20 0 1 184");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 20 0 1 187");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 20 0 1 188");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 20 0 1 190");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 20 0 1 203");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 20 0 1 204");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 20 0 1 205");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 20 0 1 206");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 20 0 1 215");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 20 0 1 217");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 20 0 1 221");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 20 0 1 224");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 20 0 1 227");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 20 0 1 231");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 20 0 1 246");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 20 0 1 266");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 20 0 1 268");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 20 0 1 272");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 20 0 1 274");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 20 0 1 278");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 20 0 1 281");
currentSystemGoal = 1;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 20 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 20 0 2 182");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 20 0 1 184");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 20 0 1 187");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 20 0 1 188");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 20 0 1 190");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 20 0 1 203");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 20 0 1 204");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 20 0 1 205");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 20 0 1 206");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 20 0 2 215");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 20 0 2 217");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 20 0 2 221");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 20 0 2 224");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 20 0 2 227");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 20 0 2 231");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 20 0 2 246");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 20 0 2 266");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 20 0 2 268");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 20 0 2 272");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 20 0 2 274");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 20 0 2 278");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 20 0 2 281");
currentSystemGoal = 2;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 20 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 20 0 3 182");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 20 0 2 184");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 20 0 2 187");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 20 0 2 188");
gameAction20(); gameAction24();
return; }
  inputEventCase156();
}
void inputEventCase156() {
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 20 0 2 190");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 20 0 2 203");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 20 0 2 204");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 20 0 2 205");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 20 0 2 206");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 20 0 3 215");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 20 0 3 217");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 20 0 3 221");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 20 0 3 224");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 20 0 3 227");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 20 0 3 231");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 20 0 3 246");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 20 0 3 266");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 20 0 3 268");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 20 0 3 272");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 20 0 3 274");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 20 0 3 278");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 20 0 3 281");
currentSystemGoal = 3;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 20 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 20 0 4 182");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 20 0 4 184");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 20 0 3 187");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 20 0 4 188");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 20 0 3 190");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 20 0 4 203");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 20 0 4 204");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 20 0 3 205");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 20 0 3 206");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 20 0 3 215");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 20 0 4 217");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 20 0 3 221");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 20 0 3 224");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 20 0 4 227");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 20 0 3 231");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 20 0 4 246");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 20 0 3 266");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 20 0 4 268");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 20 0 3 272");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 20 0 3 274");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 20 0 4 278");
currentSystemGoal = 4;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 20 0 3 281");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 20 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 20 0 5 182");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 20 0 5 184");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 20 0 5 187");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 20 0 4 188");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 20 0 4 190");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 20 0 5 203");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 20 0 4 204");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 20 0 5 205");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 20 0 4 206");
gameAction20(); gameAction24();
return; }
  inputEventCase157();
}
void inputEventCase157() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 20 0 5 215");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 20 0 4 217");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 20 0 5 221");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 20 0 4 224");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 20 0 4 227");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 20 0 4 231");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 20 0 5 246");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 20 0 5 266");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 20 0 4 268");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 20 0 5 272");
currentSystemGoal = 5;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 20 0 4 274");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 20 0 4 278");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 20 0 4 281");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 20 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 20 0 0 182");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 20 0 0 184");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 20 0 0 187");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 20 0 0 188");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 20 0 0 190");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 20 0 5 203");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 20 0 5 204");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 20 0 5 205");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 20 0 5 206");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 20 0 0 215");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 20 0 0 217");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 20 0 5 221");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 20 0 0 224");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 20 0 5 227");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 20 0 5 231");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 20 0 0 246");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 20 0 0 266");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 20 0 0 268");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 20 0 5 272");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 20 0 0 274");
currentSystemGoal = 0;
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 20 0 5 278");
gameAction20(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 20 0 5 281");
gameAction20(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_newTeamIdentifierIsFine() {
onInputAction20();
 }
 void emit_newTeamIdentifierIsFine() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_newTeamIdentifierIsFine();
      }});
 }
void onInputAction21() {
  logCurrentState("inputActionloadTeamSucceed",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 21 46 50 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 21 46 50 0 1 182");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 21 46 50 0 1 184");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 21 46 50 0 1 187");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 21 46 50 0 1 188");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 21 46 50 0 1 190");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 21 46 50 0 1 203");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 21 46 50 0 1 204");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 21 46 50 0 1 205");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 21 46 50 0 1 206");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 21 46 50 0 1 215");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 21 46 50 0 1 217");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 21 46 50 0 1 221");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 21 46 50 0 1 224");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 21 46 50 0 1 227");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 21 46 50 0 1 231");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 21 46 50 0 1 246");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 21 46 50 0 1 266");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 21 46 50 0 1 268");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 21 46 50 0 1 272");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 21 46 50 0 1 274");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 21 46 50 0 1 278");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 21 46 50 0 1 281");
currentSystemGoal = 1;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 21 46 50 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 21 46 50 0 2 182");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 21 46 50 0 1 184");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  inputEventCase158();
}
void inputEventCase158() {
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 21 46 50 0 1 187");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 21 46 50 0 1 188");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 21 46 50 0 1 190");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 21 46 50 0 1 203");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 21 46 50 0 1 204");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 21 46 50 0 1 205");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 21 46 50 0 1 206");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 21 46 50 0 2 215");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 21 46 50 0 2 217");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 21 46 50 0 2 221");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 21 46 50 0 2 224");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 21 46 50 0 2 227");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 21 46 50 0 2 231");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 21 46 50 0 2 246");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 21 46 50 0 2 266");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 21 46 50 0 2 268");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 21 46 50 0 2 272");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 21 46 50 0 2 274");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 21 46 50 0 2 278");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 21 46 50 0 2 281");
currentSystemGoal = 2;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 21 46 50 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 21 46 50 0 3 182");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 21 46 50 0 2 184");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 21 46 50 0 2 187");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 21 46 50 0 2 188");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 21 46 50 0 2 190");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  inputEventCase159();
}
void inputEventCase159() {
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 21 46 50 0 2 203");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 21 46 50 0 2 204");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 21 46 50 0 2 205");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 21 46 50 0 2 206");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 21 46 50 0 3 215");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 21 46 50 0 3 217");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 21 46 50 0 3 221");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 21 46 50 0 3 224");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 21 46 50 0 3 227");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 21 46 50 0 3 231");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 21 46 50 0 3 246");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 21 46 50 0 3 266");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 21 46 50 0 3 268");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 21 46 50 0 3 272");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 21 46 50 0 3 274");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 21 46 50 0 3 278");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 21 46 50 0 3 281");
currentSystemGoal = 3;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 21 46 50 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 21 46 50 0 4 182");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 21 46 50 0 4 184");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 21 46 50 0 3 187");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 21 46 50 0 4 188");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 21 46 50 0 3 190");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 21 46 50 0 4 203");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 21 46 50 0 4 204");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 21 46 50 0 3 205");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  inputEventCase160();
}
void inputEventCase160() {
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 21 46 50 0 3 206");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 21 46 50 0 3 215");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 21 46 50 0 4 217");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 21 46 50 0 3 221");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 21 46 50 0 3 224");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 21 46 50 0 4 227");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 21 46 50 0 3 231");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 21 46 50 0 4 246");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 21 46 50 0 3 266");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 21 46 50 0 4 268");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 21 46 50 0 3 272");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 21 46 50 0 3 274");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 21 46 50 0 4 278");
currentSystemGoal = 4;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 21 46 50 0 3 281");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 21 46 50 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 21 46 50 0 5 182");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 21 46 50 0 5 184");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 21 46 50 0 5 187");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 21 46 50 0 4 188");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 21 46 50 0 4 190");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 21 46 50 0 5 203");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 21 46 50 0 4 204");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 21 46 50 0 5 205");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 21 46 50 0 4 206");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 21 46 50 0 5 215");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 21 46 50 0 4 217");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  inputEventCase161();
}
void inputEventCase161() {
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 21 46 50 0 5 221");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 21 46 50 0 4 224");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 21 46 50 0 4 227");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 21 46 50 0 4 231");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 21 46 50 0 5 246");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 21 46 50 0 5 266");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 21 46 50 0 4 268");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 21 46 50 0 5 272");
currentSystemGoal = 5;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 21 46 50 0 4 274");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 21 46 50 0 4 278");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 21 46 50 0 4 281");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 21 46 50 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 21 46 50 0 0 182");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 21 46 50 0 0 184");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 21 46 50 0 0 187");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 21 46 50 0 0 188");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 21 46 50 0 0 190");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 21 46 50 0 5 203");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 21 46 50 0 5 204");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 21 46 50 0 5 205");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 21 46 50 0 5 206");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 21 46 50 0 0 215");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 21 46 50 0 0 217");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 21 46 50 0 5 221");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 21 46 50 0 0 224");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 21 46 50 0 5 227");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  inputEventCase162();
}
void inputEventCase162() {
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 21 46 50 0 5 231");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 21 46 50 0 0 246");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 21 46 50 0 0 266");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 21 46 50 0 0 268");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 21 46 50 0 5 272");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 21 46 50 0 0 274");
currentSystemGoal = 0;
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 21 46 50 0 5 278");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 21 46 50 0 5 281");
gameAction21(); gameAction70(); gameAction74(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_loadTeamSucceed() {
onInputAction21();
 }
 void emit_loadTeamSucceed() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_loadTeamSucceed();
      }});
 }
void onInputAction22() {
  logCurrentState("inputActionloadTeamFail",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 22 47 0 1 209");
currentSystemGoal = 1;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 22 47 0 1 182");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 22 47 0 1 184");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 22 47 0 1 187");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 22 47 0 1 188");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 22 47 0 1 190");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 22 47 0 1 203");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 22 47 0 1 204");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 22 47 0 1 205");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 22 47 0 1 206");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 22 47 0 1 215");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 22 47 0 1 217");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 22 47 0 1 221");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 22 47 0 1 224");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 22 47 0 1 227");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 22 47 0 1 231");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 22 47 0 1 246");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 22 47 0 1 266");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 22 47 0 1 268");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 22 47 0 1 272");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 22 47 0 1 274");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 22 47 0 1 278");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 22 47 0 1 281");
currentSystemGoal = 1;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 22 47 0 2 209");
currentSystemGoal = 2;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 22 47 0 2 182");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 22 47 0 1 184");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 22 47 0 1 187");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 22 47 0 1 188");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 22 47 0 1 190");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 22 47 0 1 203");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 22 47 0 1 204");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 22 47 0 1 205");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 22 47 0 1 206");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 22 47 0 2 215");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  inputEventCase163();
}
void inputEventCase163() {
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 22 47 0 2 217");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 22 47 0 2 221");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 22 47 0 2 224");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 22 47 0 2 227");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 22 47 0 2 231");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 22 47 0 2 246");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 22 47 0 2 266");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 22 47 0 2 268");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 22 47 0 2 272");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 22 47 0 2 274");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 22 47 0 2 278");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 22 47 0 2 281");
currentSystemGoal = 2;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 22 47 0 3 209");
currentSystemGoal = 3;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 22 47 0 3 182");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 22 47 0 2 184");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 22 47 0 2 187");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 22 47 0 2 188");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 22 47 0 2 190");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 22 47 0 2 203");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 22 47 0 2 204");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 22 47 0 2 205");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 22 47 0 2 206");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 22 47 0 3 215");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 22 47 0 3 217");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 22 47 0 3 221");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 22 47 0 3 224");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 22 47 0 3 227");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 22 47 0 3 231");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 22 47 0 3 246");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 22 47 0 3 266");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 22 47 0 3 268");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 22 47 0 3 272");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 22 47 0 3 274");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 22 47 0 3 278");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  inputEventCase164();
}
void inputEventCase164() {
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 22 47 0 3 281");
currentSystemGoal = 3;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 22 47 0 4 209");
currentSystemGoal = 4;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 22 47 0 4 182");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 22 47 0 4 184");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 22 47 0 3 187");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 22 47 0 4 188");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 22 47 0 3 190");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 22 47 0 4 203");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 22 47 0 4 204");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 22 47 0 3 205");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 22 47 0 3 206");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 22 47 0 3 215");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 22 47 0 4 217");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 22 47 0 3 221");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 22 47 0 3 224");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 22 47 0 4 227");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 22 47 0 3 231");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 22 47 0 4 246");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 22 47 0 3 266");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 22 47 0 4 268");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 22 47 0 3 272");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 22 47 0 3 274");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 22 47 0 4 278");
currentSystemGoal = 4;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 22 47 0 3 281");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 22 47 0 5 209");
currentSystemGoal = 5;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 22 47 0 5 182");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 22 47 0 5 184");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 22 47 0 5 187");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 22 47 0 4 188");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 22 47 0 4 190");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 22 47 0 5 203");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 22 47 0 4 204");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 22 47 0 5 205");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 22 47 0 4 206");
gameAction22(); gameAction71(); gameAction24();
return; }
  inputEventCase165();
}
void inputEventCase165() {
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 22 47 0 5 215");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 22 47 0 4 217");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 22 47 0 5 221");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 22 47 0 4 224");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 22 47 0 4 227");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 22 47 0 4 231");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 22 47 0 5 246");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 22 47 0 5 266");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 22 47 0 4 268");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 22 47 0 5 272");
currentSystemGoal = 5;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 22 47 0 4 274");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 22 47 0 4 278");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 22 47 0 4 281");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 22 47 0 0 209");
currentSystemGoal = 0;
controllerState = 209;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 22 47 0 0 182");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 22 47 0 0 184");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 22 47 0 0 187");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 22 47 0 0 188");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 22 47 0 0 190");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 22 47 0 5 203");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 22 47 0 5 204");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 22 47 0 5 205");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 22 47 0 5 206");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 22 47 0 0 215");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 22 47 0 0 217");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 22 47 0 5 221");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 22 47 0 0 224");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 22 47 0 5 227");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 22 47 0 5 231");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 22 47 0 0 246");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 22 47 0 0 266");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 22 47 0 0 268");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 22 47 0 5 272");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 22 47 0 0 274");
currentSystemGoal = 0;
gameAction22(); gameAction71(); gameAction24();
return; }
  inputEventCase166();
}
void inputEventCase166() {
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 22 47 0 5 278");
gameAction22(); gameAction71(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 22 47 0 5 281");
gameAction22(); gameAction71(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_loadTeamFail() {
onInputAction22();
 }
 void emit_loadTeamFail() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_loadTeamFail();
      }});
 }
void onInputAction23() {
  logCurrentState("inputActionactionToBeExecutedWasStored",currentSystemGoal,controllerState);
  if ((currentSystemGoal==0) && (controllerState==0)) {
Log.w("ActionLine","0 0 23 0 1 214");
currentSystemGoal = 1;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==182)) {
Log.w("ActionLine","0 182 23 45 0 1 217");
currentSystemGoal = 1;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==184)) {
Log.w("ActionLine","0 184 23 45 0 1 188");
currentSystemGoal = 1;
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==187)) {
Log.w("ActionLine","0 187 23 45 0 1 190");
currentSystemGoal = 1;
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==188)) {
Log.w("ActionLine","0 188 23 0 1 188");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==190)) {
Log.w("ActionLine","0 190 23 0 1 190");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==203)) {
Log.w("ActionLine","0 203 23 45 0 1 204");
currentSystemGoal = 1;
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==204)) {
Log.w("ActionLine","0 204 23 0 1 204");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==205)) {
Log.w("ActionLine","0 205 23 45 0 1 206");
currentSystemGoal = 1;
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==206)) {
Log.w("ActionLine","0 206 23 0 1 206");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==215)) {
Log.w("ActionLine","0 215 23 45 0 1 224");
currentSystemGoal = 1;
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==217)) {
Log.w("ActionLine","0 217 23 0 1 217");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==221)) {
Log.w("ActionLine","0 221 23 45 0 1 231");
currentSystemGoal = 1;
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==224)) {
Log.w("ActionLine","0 224 23 0 1 224");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==227)) {
Log.w("ActionLine","0 227 23 0 1 227");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==231)) {
Log.w("ActionLine","0 231 23 0 1 231");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==246)) {
Log.w("ActionLine","0 246 23 45 0 1 268");
currentSystemGoal = 1;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==266)) {
Log.w("ActionLine","0 266 23 45 0 1 274");
currentSystemGoal = 1;
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==268)) {
Log.w("ActionLine","0 268 23 0 1 268");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==272)) {
Log.w("ActionLine","0 272 23 45 0 1 281");
currentSystemGoal = 1;
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==274)) {
Log.w("ActionLine","0 274 23 0 1 274");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==278)) {
Log.w("ActionLine","0 278 23 0 1 278");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==0) && (controllerState==281)) {
Log.w("ActionLine","0 281 23 0 1 281");
currentSystemGoal = 1;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==0)) {
Log.w("ActionLine","1 0 23 0 2 214");
currentSystemGoal = 2;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==182)) {
Log.w("ActionLine","1 182 23 45 0 2 217");
currentSystemGoal = 2;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==184)) {
Log.w("ActionLine","1 184 23 45 0 1 188");
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==187)) {
Log.w("ActionLine","1 187 23 45 0 1 190");
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==188)) {
Log.w("ActionLine","1 188 23 0 1 188");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==190)) {
Log.w("ActionLine","1 190 23 0 1 190");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==203)) {
Log.w("ActionLine","1 203 23 45 0 1 204");
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==204)) {
Log.w("ActionLine","1 204 23 0 1 204");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==205)) {
Log.w("ActionLine","1 205 23 45 0 1 206");
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==206)) {
Log.w("ActionLine","1 206 23 0 1 206");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==215)) {
Log.w("ActionLine","1 215 23 45 0 2 224");
currentSystemGoal = 2;
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==217)) {
Log.w("ActionLine","1 217 23 0 2 217");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==221)) {
Log.w("ActionLine","1 221 23 45 0 2 231");
currentSystemGoal = 2;
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==224)) {
Log.w("ActionLine","1 224 23 0 2 224");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==227)) {
Log.w("ActionLine","1 227 23 0 2 227");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==231)) {
Log.w("ActionLine","1 231 23 0 2 231");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==246)) {
Log.w("ActionLine","1 246 23 45 0 2 268");
currentSystemGoal = 2;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==266)) {
Log.w("ActionLine","1 266 23 45 0 2 274");
currentSystemGoal = 2;
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  inputEventCase167();
}
void inputEventCase167() {
  if ((currentSystemGoal==1) && (controllerState==268)) {
Log.w("ActionLine","1 268 23 0 2 268");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==272)) {
Log.w("ActionLine","1 272 23 45 0 2 281");
currentSystemGoal = 2;
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==274)) {
Log.w("ActionLine","1 274 23 0 2 274");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==278)) {
Log.w("ActionLine","1 278 23 0 2 278");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==1) && (controllerState==281)) {
Log.w("ActionLine","1 281 23 0 2 281");
currentSystemGoal = 2;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==0)) {
Log.w("ActionLine","2 0 23 0 3 214");
currentSystemGoal = 3;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==182)) {
Log.w("ActionLine","2 182 23 45 0 3 217");
currentSystemGoal = 3;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==184)) {
Log.w("ActionLine","2 184 23 45 0 2 188");
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==187)) {
Log.w("ActionLine","2 187 23 45 0 2 190");
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==188)) {
Log.w("ActionLine","2 188 23 0 2 188");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==190)) {
Log.w("ActionLine","2 190 23 0 2 190");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==203)) {
Log.w("ActionLine","2 203 23 45 0 2 204");
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==204)) {
Log.w("ActionLine","2 204 23 0 2 204");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==205)) {
Log.w("ActionLine","2 205 23 45 0 2 206");
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==206)) {
Log.w("ActionLine","2 206 23 0 2 206");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==215)) {
Log.w("ActionLine","2 215 23 45 0 3 224");
currentSystemGoal = 3;
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==217)) {
Log.w("ActionLine","2 217 23 0 3 217");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==221)) {
Log.w("ActionLine","2 221 23 45 0 3 231");
currentSystemGoal = 3;
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==224)) {
Log.w("ActionLine","2 224 23 0 3 224");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==227)) {
Log.w("ActionLine","2 227 23 0 3 227");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==231)) {
Log.w("ActionLine","2 231 23 0 3 231");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==246)) {
Log.w("ActionLine","2 246 23 45 0 3 268");
currentSystemGoal = 3;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==266)) {
Log.w("ActionLine","2 266 23 45 0 3 274");
currentSystemGoal = 3;
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==268)) {
Log.w("ActionLine","2 268 23 0 3 268");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==272)) {
Log.w("ActionLine","2 272 23 45 0 3 281");
currentSystemGoal = 3;
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==274)) {
Log.w("ActionLine","2 274 23 0 3 274");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==278)) {
Log.w("ActionLine","2 278 23 0 3 278");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==2) && (controllerState==281)) {
Log.w("ActionLine","2 281 23 0 3 281");
currentSystemGoal = 3;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==0)) {
Log.w("ActionLine","3 0 23 0 4 214");
currentSystemGoal = 4;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==182)) {
Log.w("ActionLine","3 182 23 45 0 4 217");
currentSystemGoal = 4;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==184)) {
Log.w("ActionLine","3 184 23 45 0 4 188");
currentSystemGoal = 4;
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==187)) {
Log.w("ActionLine","3 187 23 45 0 3 190");
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==188)) {
Log.w("ActionLine","3 188 23 0 4 188");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==190)) {
Log.w("ActionLine","3 190 23 0 3 190");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==203)) {
Log.w("ActionLine","3 203 23 45 0 4 204");
currentSystemGoal = 4;
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==204)) {
Log.w("ActionLine","3 204 23 0 4 204");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==205)) {
Log.w("ActionLine","3 205 23 45 0 3 206");
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==206)) {
Log.w("ActionLine","3 206 23 0 3 206");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==215)) {
Log.w("ActionLine","3 215 23 45 0 3 224");
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==217)) {
Log.w("ActionLine","3 217 23 0 4 217");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==221)) {
Log.w("ActionLine","3 221 23 45 0 3 231");
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==224)) {
Log.w("ActionLine","3 224 23 0 3 224");
gameAction23(); gameAction24();
return; }
  inputEventCase168();
}
void inputEventCase168() {
  if ((currentSystemGoal==3) && (controllerState==227)) {
Log.w("ActionLine","3 227 23 0 4 227");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==231)) {
Log.w("ActionLine","3 231 23 0 3 231");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==246)) {
Log.w("ActionLine","3 246 23 45 0 4 268");
currentSystemGoal = 4;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==266)) {
Log.w("ActionLine","3 266 23 45 0 3 274");
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==268)) {
Log.w("ActionLine","3 268 23 0 4 268");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==272)) {
Log.w("ActionLine","3 272 23 45 0 3 281");
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==274)) {
Log.w("ActionLine","3 274 23 0 3 274");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==278)) {
Log.w("ActionLine","3 278 23 0 4 278");
currentSystemGoal = 4;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==3) && (controllerState==281)) {
Log.w("ActionLine","3 281 23 0 3 281");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==0)) {
Log.w("ActionLine","4 0 23 0 5 214");
currentSystemGoal = 5;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==182)) {
Log.w("ActionLine","4 182 23 45 0 5 217");
currentSystemGoal = 5;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==184)) {
Log.w("ActionLine","4 184 23 45 0 5 188");
currentSystemGoal = 5;
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==187)) {
Log.w("ActionLine","4 187 23 45 0 5 190");
currentSystemGoal = 5;
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==188)) {
Log.w("ActionLine","4 188 23 0 4 188");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==190)) {
Log.w("ActionLine","4 190 23 0 4 190");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==203)) {
Log.w("ActionLine","4 203 23 45 0 5 204");
currentSystemGoal = 5;
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==204)) {
Log.w("ActionLine","4 204 23 0 4 204");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==205)) {
Log.w("ActionLine","4 205 23 45 0 5 206");
currentSystemGoal = 5;
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==206)) {
Log.w("ActionLine","4 206 23 0 4 206");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==215)) {
Log.w("ActionLine","4 215 23 45 0 5 224");
currentSystemGoal = 5;
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==217)) {
Log.w("ActionLine","4 217 23 0 4 217");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==221)) {
Log.w("ActionLine","4 221 23 45 0 5 231");
currentSystemGoal = 5;
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==224)) {
Log.w("ActionLine","4 224 23 0 4 224");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==227)) {
Log.w("ActionLine","4 227 23 0 4 227");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==231)) {
Log.w("ActionLine","4 231 23 0 4 231");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==246)) {
Log.w("ActionLine","4 246 23 45 0 5 268");
currentSystemGoal = 5;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==266)) {
Log.w("ActionLine","4 266 23 45 0 5 274");
currentSystemGoal = 5;
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==268)) {
Log.w("ActionLine","4 268 23 0 4 268");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==272)) {
Log.w("ActionLine","4 272 23 45 0 5 281");
currentSystemGoal = 5;
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==274)) {
Log.w("ActionLine","4 274 23 0 4 274");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==278)) {
Log.w("ActionLine","4 278 23 0 4 278");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==4) && (controllerState==281)) {
Log.w("ActionLine","4 281 23 0 4 281");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==0)) {
Log.w("ActionLine","5 0 23 0 0 214");
currentSystemGoal = 0;
controllerState = 214;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==182)) {
Log.w("ActionLine","5 182 23 45 0 0 217");
currentSystemGoal = 0;
controllerState = 217;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==184)) {
Log.w("ActionLine","5 184 23 45 0 0 188");
currentSystemGoal = 0;
controllerState = 188;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==187)) {
Log.w("ActionLine","5 187 23 45 0 0 190");
currentSystemGoal = 0;
controllerState = 190;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==188)) {
Log.w("ActionLine","5 188 23 0 0 188");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==190)) {
Log.w("ActionLine","5 190 23 0 0 190");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==203)) {
Log.w("ActionLine","5 203 23 45 0 5 204");
controllerState = 204;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==204)) {
Log.w("ActionLine","5 204 23 0 5 204");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==205)) {
Log.w("ActionLine","5 205 23 45 0 5 206");
controllerState = 206;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==206)) {
Log.w("ActionLine","5 206 23 0 5 206");
gameAction23(); gameAction24();
return; }
  inputEventCase169();
}
void inputEventCase169() {
  if ((currentSystemGoal==5) && (controllerState==215)) {
Log.w("ActionLine","5 215 23 45 0 0 224");
currentSystemGoal = 0;
controllerState = 224;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==217)) {
Log.w("ActionLine","5 217 23 0 0 217");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==221)) {
Log.w("ActionLine","5 221 23 45 0 5 231");
controllerState = 231;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==224)) {
Log.w("ActionLine","5 224 23 0 0 224");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==227)) {
Log.w("ActionLine","5 227 23 0 5 227");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==231)) {
Log.w("ActionLine","5 231 23 0 5 231");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==246)) {
Log.w("ActionLine","5 246 23 45 0 0 268");
currentSystemGoal = 0;
controllerState = 268;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==266)) {
Log.w("ActionLine","5 266 23 45 0 0 274");
currentSystemGoal = 0;
controllerState = 274;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==268)) {
Log.w("ActionLine","5 268 23 0 0 268");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==272)) {
Log.w("ActionLine","5 272 23 45 0 5 281");
controllerState = 281;
gameAction23(); gameAction69(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==274)) {
Log.w("ActionLine","5 274 23 0 0 274");
currentSystemGoal = 0;
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==278)) {
Log.w("ActionLine","5 278 23 0 5 278");
gameAction23(); gameAction24();
return; }
  if ((currentSystemGoal==5) && (controllerState==281)) {
Log.w("ActionLine","5 281 23 0 5 281");
gameAction23(); gameAction24();
return; }
 Log.e("Action","Failure -- Case uncovered.");
}
 void on_actionToBeExecutedWasStored() {
onInputAction23();
 }
 void emit_actionToBeExecutedWasStored() {
      (new Handler(Looper.getMainLooper())).post(new java.lang.Thread() { public void run() {
         on_actionToBeExecutedWasStored();
      }});
 }
      int selectedItem_listViewAvailableTeams = -1;
                
    // --SYNTHESIZED-CODE-SUBCLASSES-END--



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedpreferences = this.getPreferences(0); // Get preferences file (0 = no option flags set)
        sharedPreferencesLock = new ReentrantLock();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        newTeamMemberAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, newTeamMembers);

        ListView lv = findViewById(R.id.memberList);
        lv.setAdapter(newTeamMemberAdapter);

        teamsAvailableAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, teamsAvailable);

        lv = findViewById(R.id.listViewAvailableTeams);
        lv.setAdapter(teamsAvailableAdapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // The Spinner for reimbursement
        membersAvailableForReimbursementAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, membersAvailableForReimbursement);

        Spinner spin = findViewById(R.id.spinnerReimbursementPerson);
        spin.setAdapter(membersAvailableForReimbursementAdapter);


        updateAvailableTeamsList();

        WebView wv = findViewById(R.id.webViewOverview);
        wv.setVerticalScrollBarEnabled(true);
        wv.setHorizontalScrollBarEnabled(true);


        // Set Chrome as web view type
        final WebView webView = findViewById(R.id.webViewExpensesToApprove);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        /*webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("MSG", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return super.onConsoleMessage(consoleMessage);
            }
        });*/

        wv = findViewById(R.id.webViewOfflineActions);
        wv.setVerticalScrollBarEnabled(true);
        wv.setHorizontalScrollBarEnabled(true);





        // --SYNTHESIZED-CODE-ON-CREATE-START--
onInputAction0();
      { final Button k = findViewById(R.id.buttonAddExpense);
    k.setOnClickListener(new Button.OnClickListener() {
        public void onClick(View v) {
                onInputAction1();
                }
        }); }
      { final Button k = findViewById(R.id.buttonReimburse);
    k.setOnClickListener(new Button.OnClickListener() {
        public void onClick(View v) {
                onInputAction2();
                }
        }); }
      { final Button k = findViewById(R.id.buttonAddMember);
    k.setOnClickListener(new Button.OnClickListener() {
        public void onClick(View v) {
                onInputAction3();
                }
        }); }
      { final Button k = findViewById(R.id.buttonAddTeam);
    k.setOnClickListener(new Button.OnClickListener() {
        public void onClick(View v) {
                onInputAction4();
                }
        }); }
      { final ListView k = findViewById(R.id.listViewAvailableTeams);
    k.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectedItem_listViewAvailableTeams = i;
                onInputAction5();
            }
        }); }
      { final Button k = findViewById(R.id.buttonLoginTeam);
    k.setOnClickListener(new Button.OnClickListener() {
        public void onClick(View v) {
                onInputAction6();
                }
        }); }
Log.w("Action","Application start");
        // --SYNTHESIZED-CODE-ON-CREATE-END--

        { final Spinner k = findViewById(R.id.spinnerReimbursementPerson);
            k.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    selectedItem_spinnerReimbursementPerson = i;
                }
                public void onNothingSelected(AdapterView<?> adapterView) {
                    selectedItem_spinnerReimbursementPerson = -1;
                }
            }); }
        
       
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.main, menu); -- No menu!
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        Log.w("EVT","ItemSelected");

        if (id == R.id.MenuItemSelectAccount) {
            on_MenuItemSelectAccount();
        } else if (id == R.id.MenuItemAddExpense) {
            on_MenuItemAddExpense();
        } else if (id == R.id.MenuItemExpenseList) {
            on_MenuItemExpenseList();
        } else if (id == R.id.MenuItemNewTeam) {
            on_MenuItemNewTeam();
        } else if (id == R.id.MenuItemNonApprovedExpenses) {
            on_MenuItemNonApprovedExpenses();
        } else if (id == R.id.MenuItemOfflineActions) {
            on_MenuItemOfflineActions();
        } else if (id == R.id.MenuItemOverview) {
            on_MenuItemOverview();
        } else if (id == R.id.MenuItemTeamAdministration) {
            on_MenuItemTeamAdministration();
        } else if (id == R.id.MenuItemTrackReimbursement) {
            on_MenuItemTrackReimbursement();
        }


/*
            View l = findViewById(R.id.SelectAccount);
            l.setVisibility(View.VISIBLE);
            l = findViewById(R.id.ExpenseList);
            l.setVisibility(View.INVISIBLE);
            Log.w("EVT","SelectAccount");
        } */

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
