package com.odoo.addons.crm;

import java.util.ArrayList;
import java.util.List;

import odoo.OArguments;
import odoo.Odoo;
import odoo.controls.OForm;
import odoo.controls.OList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;

import com.odoo.addons.crm.model.CRMLead;
import com.odoo.addons.crm.model.CRMLead.CRMCaseStage;
import com.odoo.crm.R;
import com.odoo.orm.ODataRow;
import com.odoo.orm.OValues;
import com.odoo.support.ODialog;
import com.odoo.support.fragment.BaseFragment;
import com.odoo.util.OControls;
import com.odoo.util.drawer.DrawerItem;

public class CRMConvertToOpp extends BaseFragment implements
		OnCheckedChangeListener {

	View mView = null;
	Bundle args = null;
	OForm mForm = null;
	int index = 0;
	ODataRow mLead = null;
	ODataRow mCustomer = null;
	List<Object> mOpportunities = new ArrayList<Object>();
	boolean mMergeOpportunity = false;
	RadioButton rdoConvertOpp, rdoMergeOpp;
	OList mLeadList = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		initArgs();
		mView = inflater.inflate(R.layout.crm_convert_to_opportunity,
				container, false);
		initFormControls();
		return mView;
	}

	public void initFormControls() {
		mForm = (OForm) mView.findViewById(R.id.crmConvertToOpp);
		mLeadList = (OList) mForm.findViewById(R.id.crmLeadOppList);
		rdoConvertOpp = (RadioButton) mForm
				.findViewById(R.id.rdoConvertToOpportunity);
		rdoMergeOpp = (RadioButton) mForm
				.findViewById(R.id.rdoMergeExistingOpportunity);
		mForm.setEditable(false);
		initFormData();
		fillLeadList();
		rdoConvertOpp.setOnCheckedChangeListener(this);
		rdoMergeOpp.setOnCheckedChangeListener(this);
	}

	public void initFormData() {
		if (args != null && args.containsKey("lead_id")) {
			index = args.getInt("index");
			mLead = db().select(args.getInt("lead_id"));
			mForm.initForm(mLead);
			mCustomer = mLead.getM2ORecord("partner_id").browse();
			if (mCustomer != null) {
				CRMCaseStage stage = new CRMCaseStage(getActivity());
				List<ODataRow> stages = stage.select("name = ?",
						new String[] { "Dead" });
				String stage_id = stages.get(0).getString("id");
				ODataRow mParent = mCustomer.getM2ORecord("parent_id").browse();
				String where = "partner_id = ? and stage_id != ?";
				String whereArgs[] = new String[] { mCustomer.getString("id"),
						stage_id };
				if (mParent != null) {
					where = "partner_id = ? or partner_id = ? and stage_id != ?";
					whereArgs = new String[] { mCustomer.getString("id"),
							mParent.getString("id"), stage_id };
				}
				mOpportunities.addAll(db().select(where, whereArgs));
			}
		}
	}

	public void fillLeadList() {
		List<ODataRow> list = new ArrayList<ODataRow>();
		for (Object r : mOpportunities) {
			list.add((ODataRow) r);
		}
		mLeadList.setCustomView(R.layout.crm_lead_list_item);
		mLeadList.initListControl(list);
	}

	public void initArgs() {
		args = getArguments();
	}

	@Override
	public Object databaseHelper(Context context) {
		return new CRMLead(context);
	}

	@Override
	public List<DrawerItem> drawerMenus(Context context) {
		return null;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		inflater.inflate(R.menu.menu_crm_convert_to_opp, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_crm_convert:
			break;
		case R.id.menu_crm_convert_cancel:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		ConvertOpportunity convert = new ConvertOpportunity();
		convert.execute();
		if (buttonView.getId() == R.id.rdoConvertToOpportunity) {
			if (isChecked) {
				mMergeOpportunity = false;
				OControls.setGone(mView, R.id.crmLeadOppList);
			} else {
				mMergeOpportunity = true;
				OControls.setVisible(mView, R.id.crmLeadOppList);
			}
		}
		if (buttonView.getId() == R.id.rdoMergeExistingOpportunity) {
			if (isChecked) {
				mMergeOpportunity = true;
				OControls.setVisible(mView, R.id.crmLeadOppList);
			} else {
				mMergeOpportunity = false;
				OControls.setGone(mView, R.id.crmLeadOppList);
			}
		}
	}

	class ConvertOpportunity extends AsyncTask<Void, Void, Void> {

		ODialog mProgress = null;
		String mToast = null;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mProgress = new ODialog(getActivity(), false,
					_s(R.string.process_converting_to_opp));
			mProgress.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			scope.main().runOnUiThread(new Runnable() {

				@Override
				public void run() {
					try {
						Odoo odoo = app().getOdoo();
						if (odoo != null) {
							OValues values = new OValues();
							List<Integer> ids = new ArrayList<Integer>();
							String name = "convert";
							if (mMergeOpportunity && mOpportunities.size() > 1) {
								name = "merge";
								for (Object row : mOpportunities) {
									ODataRow r = (ODataRow) row;
									ids.add(r.getInt("id"));
								}
							}

							values.put("name", name);
							values.put("action", (mCustomer != null) ? "exist"
									: "create");
							values.put(
									"partner_id",
									(mCustomer != null) ? mCustomer
											.getInt("id") : false);
							JSONObject context = new JSONObject();
							context.put("stage_type", "lead");
							context.put("active_id", mLead.getInt("id"));
							context.put("active_ids",
									new JSONArray().put(mLead.getInt("id")));
							context.put("active_model", "crm.lead");
							odoo.updateContext(context);
							JSONObject args = new JSONObject();
							for (String key : values.keys()) {
								args.put(key, values.get(key));
							}
							JSONObject result = odoo.createNew(
									"crm.lead2opportunity.partner", args);
							int lead_to_opp_partner_id = result
									.getInt("result");

							// Action Apply
							OArguments arg = new OArguments();
							arg.add(new JSONArray().put(lead_to_opp_partner_id));
							odoo.call_kw("crm.lead2opportunity.partner",
									"action_apply", null);
							OValues vals = new OValues();
							vals.put("type", "opportunity");
							db().update(vals, mLead.getInt("id"));
						} else {
							mToast = _s(R.string.toast_no_netowrk);
						}
					} catch (Exception e) {
						e.printStackTrace();
						mToast = _s(R.string.toast_no_netowrk);
					}
				}
			});
			return null;
		}
	}
}
