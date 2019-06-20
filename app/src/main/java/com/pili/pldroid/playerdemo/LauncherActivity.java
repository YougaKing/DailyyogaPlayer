package com.pili.pldroid.playerdemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pili.pldroid.player.AVOptions;
import com.pili.pldroid.playerdemo.model.Program;
import com.pili.pldroid.playerdemo.model.Session;
import com.pili.pldroid.playerdemo.utils.AssetsUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class LauncherActivity extends AppCompatActivity {


    private RecyclerView mRecyclerView;
    private List<Session> mSessionList = new ArrayList<>();
    private InnerAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_launcher);

        mRecyclerView = findViewById(R.id.recyclerView);


        String json = AssetsUtil.readAssetJson(this, "resource.json");
        Log.d("json", json);
        Type type = new TypeToken<List<Program>>() {
        }.getType();
        List<Program> programList = new Gson().fromJson(json, type);
        if (programList != null) {
            for (Program program : programList) {
                List<Session> sessionList = program.sessionList;
                if (sessionList != null && !sessionList.isEmpty()) {
                    sessionList.get(0).program_name = program.name;
                    mSessionList.addAll(sessionList);
                }
            }
        }
        mAdapter = new InnerAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    public class InnerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_program, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bindPosition(position);
        }

        @Override
        public int getItemCount() {
            return mSessionList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            @BindView(R.id.tv_program_name)
            TextView tvProgramName;
            @BindView(R.id.btn_session_name)
            Button btnSessionName;

            ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }

            public void bindPosition(int position) {
                final Session session = mSessionList.get(position);
                tvProgramName.setText(session.program_name);
                tvProgramName.setVisibility(TextUtils.isEmpty(session.program_name) ? View.GONE : View.VISIBLE);
                btnSessionName.setText(session.name);

                btnSessionName.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        gotoPlay(session);
                    }
                });
            }
        }
    }

    private void gotoPlay(Session session) {
        Intent intent = new Intent(this, PLVideoViewActivity.class);
        intent.putExtra("videoPath", session.video_url);
        intent.putExtra("mediaCodec", AVOptions.MEDIA_CODEC_AUTO);
        startActivity(intent);
    }
}
