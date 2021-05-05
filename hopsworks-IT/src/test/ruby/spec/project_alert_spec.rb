=begin
 This file is part of Hopsworks
 Copyright (C) 2021, Logical Clocks AB. All rights reserved

 Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 the GNU Affero General Public License as published by the Free Software Foundation,
 either version 3 of the License, or (at your option) any later version.

 Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 PURPOSE.  See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/>.
=end

describe "On #{ENV['OS']}" do
  after(:all) {clean_all_test_projects(spec: "project_alert")}
  describe 'Alert' do
    context 'without authentication' do
      before :all do
        with_valid_project
        create_project_alerts(@project)
        reset_session
      end
      it "should fail to get" do
        get_project_alerts(@project)
        expect_status_details(401)
      end
      it "should fail to create" do
        create_project_alert(@project, get_project_alert_failed)
        expect_status_details(401)
      end
    end
    context 'with authentication' do
      before :all do
        with_valid_project
        create_project_alerts(@project)
      end
      it "should get" do
        get_project_alerts(@project)
        expect_status_details(200)
        expect(json_body[:count]).to eq(4)
      end
      it "should update" do
        get_project_alerts(@project)
        alert = json_body[:items].detect { |a| a[:alertType] == "GLOBAL_ALERT_EMAIL" and a[:service] == "JOBS" }
        alert[:alertType] = "PROJECT_ALERT"
        update_project_alert(@project, alert[:id], alert)
        expect_status_details(200)
        get_project_alert(@project, alert[:id])
        expect(json_body).to eq(alert)
      end
      it "should fail to update to SYSTEM_ALERT" do
        get_project_alerts(@project)
        alert = json_body[:items].detect { |a| a[:alertType] == "GLOBAL_ALERT_EMAIL" and a[:service] == "FEATURESTORE" }
        alert[:alertType] = "SYSTEM_ALERT"
        update_project_alert(@project, alert[:id], alert)
        expect_status_details(400)
      end
      it "should fail to update if duplicate" do
        get_project_alerts(@project)
        alert = json_body[:items].detect { |a| a[:status] == "VALIDATION_FAILURE" and a[:service] == "FEATURESTORE" }
        alert[:status] = "VALIDATION_SUCCESS"
        update_project_alert(@project, alert[:id], alert)
        expect_status_details(400)
      end
      it "should create" do
        create_project_alert(@project, get_project_alert_failed)
        expect_status_details(201)

        get_project_alerts(@project)
        expect(json_body[:count]).to eq(5)
      end
      it "should fail to create duplicate" do
        create_project_alert(@project, get_project_alert_failed)
        expect_status_details(400)
      end
      it "should fail to create SYSTEM_ALERT" do
        alert = get_project_alert_warning
        alert[:alertType] = "SYSTEM_ALERT"
        create_project_alert(@project, alert)
        expect_status_details(400)
      end
      it "should delete alert" do
        get_project_alerts(@project)
        alert = json_body[:items].detect { |a| a[:status] == "VALIDATION_FAILURE" and a[:service] == "FEATURESTORE" }
        delete_project_alert(@project, alert[:id])
        expect_status_details(204)

        get_project_alerts(@project)
        expect(json_body[:count]).to eq(4)
      end
      context 'sort and filter' do
        before :all do
          create_project_alert(@project, get_project_alert_failure)
          create_project_alert(@project, get_project_alert_warning)
          create_project_alert(@project, get_project_alert_failed)
        end
        it "should sort by ID" do
          get_project_alerts(@project, query: "?sort_by=id:desc")
          expect_status_details(200)
          sortedRes = json_body[:items].map { |a| a[:id] }
          expect(sortedRes).to eq(sortedRes.sort.reverse)
        end
        it "should sort by TYPE" do
          get_project_alerts(@project, query: "?sort_by=TYPE:asc")
          expect_status_details(200)
          sortedRes = json_body[:items].map { |a| "#{a[:alertType]}" }
          expect(sortedRes).to eq(sortedRes.sort)
        end
        it "should sort by STATUS" do
          get_project_alerts(@project, query: "?sort_by=STATUS")
          expect_status_details(200)
          sortedRes = json_body[:items].map { |a| "#{a[:status]}" }
          expect(sortedRes).to eq(sortedRes.sort)
        end
        it "should sort by SEVERITY" do
          get_project_alerts(@project, query: "?sort_by=SEVERITY:desc")
          expect_status_details(200)
          sortedRes = json_body[:items].map { |a| "#{a[:severity]}" }
          expect(sortedRes).to eq(sortedRes.sort.reverse)
        end
        it "should sort by SEVERITY and ID" do
          get_project_alerts(@project, query: "?sort_by=SEVERITY:desc,id:asc")
          expect_status_details(200)
          sortedRes = json_body[:items].map { |o| "#{o[:severity]} #{o[:id]}" }
          s = json_body[:items].sort do |a, b|
            res = -(a[:severity] <=> b[:severity])
            res = (a[:id] <=> b[:id]) if res == 0
            res
          end
          sorted = s.map { |o| "#{o[:severity]} #{o[:id]}" }
          expect(sortedRes).to eq(sorted)
        end
        it "should filter by service" do
          get_project_alerts(@project, query: "?filter_by=service:JOBS")
          expect_status_details(200)
          expect(json_body[:count]).to eq(3)
        end
      end
    end
  end
end
