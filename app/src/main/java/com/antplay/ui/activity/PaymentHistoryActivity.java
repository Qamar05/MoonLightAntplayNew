package com.antplay.ui.activity;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.antplay.R;
import com.antplay.api.APIClient;
import com.antplay.api.RetrofitAPI;
import com.antplay.models.Payment;
import com.antplay.models.PaymentHistory_modal;
import com.antplay.ui.adapter.PaymentHistoryAdapter;
import com.antplay.utils.AppUtils;
import com.antplay.utils.Const;
import com.antplay.utils.SharedPreferenceUtils;


import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentHistoryActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    List<Payment> paymentHistoryList;
    LinearLayout linear_back;
    private ProgressBar loadingPB;
    Context mContext;
    RetrofitAPI retrofitAPI;
    PaymentHistoryAdapter paymentHistoryAdapter;
    TextView tvNoDataFound;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_history);
        mContext  =  PaymentHistoryActivity.this;
        paymentHistoryList = new ArrayList<Payment>();
        retrofitAPI = APIClient.getRetrofitInstance().create(RetrofitAPI.class);
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccentDark_light, this.getTheme()));
        recyclerView=(RecyclerView) findViewById(R.id.recyclerView_payment);
        linear_back= (LinearLayout) findViewById(R.id.back_linear_payment);
        loadingPB = (ProgressBar) findViewById(R.id.loading_getPaymentHistory);
        tvNoDataFound = findViewById(R.id.tvNoDataFound);
        linear_back.setOnClickListener(v -> finish());
        if(AppUtils.isOnline(mContext))
            callPaymentHistoryAPI();
        else
            AppUtils.showInternetDialog(mContext);

    }


    private void callPaymentHistoryAPI() {
            loadingPB.setVisibility(View.VISIBLE);
            Call<PaymentHistory_modal> call = retrofitAPI.getPaymentHistory("Bearer " + SharedPreferenceUtils.getString(PaymentHistoryActivity.this, Const.ACCESS_TOKEN));
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<PaymentHistory_modal> call, Response<PaymentHistory_modal> response) {
                    loadingPB.setVisibility(View.GONE);
                    if(response.code()==Const.SUCCESS_CODE_200) {
                        paymentHistoryList = response.body().getData();
                        if (paymentHistoryList != null && paymentHistoryList.size() > 0) {
                            paymentHistoryAdapter = new PaymentHistoryAdapter(mContext, response.body().getData());
                            recyclerView.setAdapter(paymentHistoryAdapter);
                        }
                    }
                    else if (response.code()==Const.ERROR_CODE_400||
                            response.code()==Const.ERROR_CODE_500 ||
                            response.code()==Const.ERROR_CODE_404){
                        openDialog(getString(R.string.noPaymentHistory));
                        tvNoDataFound.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onFailure(Call<PaymentHistory_modal> call, Throwable t) {
                    AppUtils.showToast(Const.something_went_wrong, PaymentHistoryActivity.this);
                }
            });
    }

    private void openDialog(String msg) {
        Dialog dialog = new Dialog(PaymentHistoryActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_logout);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView titleText =  dialog.findViewById(R.id.titleText);
        TextView msgText =  dialog.findViewById(R.id.msgText);
        Button txtNo = dialog.findViewById(R.id.txtNo);
        Button txtYes = dialog.findViewById(R.id.txtYes);
        titleText.setText(getResources().getString(R.string.no_vm_title));
        msgText.setText(msg);
        txtYes.setVisibility(View.GONE);
        txtNo.setText("Ok");

        txtNo.setOnClickListener(view -> {
            dialog.dismiss();
        });
        dialog.show();
    }

}