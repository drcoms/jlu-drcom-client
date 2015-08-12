package com.example.drcom;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {
	private String ipaddr;
	private String _account;
	private String _mac;
	private String _password;
	private EditText account;
	private EditText password;
	private EditText mac;
	private EditText ip;
	private Button B_login;
	private SharedPreferences sp;
	

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = this.getSharedPreferences("userInfo", Context.MODE_PRIVATE);
        account = (EditText)this.findViewById(R.id.editText1);
        password = (EditText)this.findViewById(R.id.editText2);
        mac = (EditText)this.findViewById(R.id.editText3);
        ip = (EditText)this.findViewById(R.id.editText4);
        B_login = (Button)this.findViewById(R.id.button1);
        
        account.setText(sp.getString("account", ""));
        password.setText(sp.getString("password", ""));
        mac.setText(sp.getString("mac", ""));
        ip.setText(sp.getString("ip", ""));


		B_login.setOnClickListener(new View.OnClickListener() {

		   public void onClick(View v){
		        ipaddr = ip.getText().toString();
		        _account = account.getText().toString();
		        _password = password.getText().toString();
		        _mac = mac.getText().toString();
		        Editor editor = sp.edit();  
                editor.putString("account", _account);  
                editor.putString("password",_password); 
                editor.putString("ip", ipaddr);
                editor.putString("mac", _mac);
                editor.commit();  
			   new Thread(new LoginThread(_account, ipaddr, _password, _mac)).start();
			   new Thread(new KeepThread()).start();

					
		   }
		   
		});
		
    }
        


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    public class LoginThread implements Runnable{
    	private String dusr;
    	private String dipaddr;
    	private String dpwd;
    	private String dmac;
    	
    	public LoginThread(String usr, String ipaddr, String pwd, String mac){
    		this.dusr = usr;
    		this.dipaddr = ipaddr;
    		this.dpwd = pwd;
    		this.dmac = mac;
    		
    	}
    	@Override
    	public void run(){
    		String svr = "10.100.61.3";
    		try {
				login.dr_login(this.dusr, this.dipaddr, this.dpwd, svr, this.dmac);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    	}
    	
    }
    
    public class KeepThread implements Runnable{
    	@Override
    	public void run(){
    		String svr = "10.100.61.3";
    		try {
				keep_alive.keep(svr);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
    
    
}
