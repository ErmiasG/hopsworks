=begin
 Copyright (C) 2020, Logical Clocks AB. All rights reserved
=end
describe "On #{ENV['OS']}" do
  after(:all) {clean_all_test_projects}
  before(:all) do
    if ENV['OS'] == "ubuntu"
      skip "These tests do not run on ubuntu"
    end
  end

  job_python_1 = "demo_job_1"
  job_python_2 = "demo_job_2"
  job_python_3 = "demo_job_3"

  describe 'job' do
    context 'without authentication' do
      before :all do
        with_valid_project
        reset_session
      end
      it "should fail" do
        get_job(@project[:id], 1, nil)
        expect_json(errorCode: 200003)
        expect_status(401)
      end
    end
    context 'with authentication create, delete, get' do
      before :all do
        with_valid_tour_project("spark")
      end
      after :each do
        clean_jobs(@project[:id])
      end
      it "should create three python jobs" do
        create_python_job(@project, job_python_1, "py")
        expect_status(201)
        create_python_job(@project, job_python_2, "py")
        expect_status(201)
        create_python_job(@project, job_python_3, "py")
        expect_status(201)
      end
      it "should get a single python job" do
        create_python_job(@project, job_python_1, "py")
        get_job(@project[:id], job_python_1, nil)
        expect_status(200)
      end
      it "should get python job dto with href" do
        create_python_job(@project, job_python_1, "py")
        get_job(@project[:id], job_python_1, nil)
        expect_status(200)
        #validate href
        expect(URI(json_body[:href]).path).to eq "#{ENV['HOPSWORKS_API']}/project/#{@project[:id]}/jobs/" + job_python_1
        expect(json_body[:config][:type]).to eq "pythonJobConfiguration"
        expect(json_body[:jobType]).to eq "PYTHON"
      end
      it "should get three jobs" do
        create_python_job(@project, job_python_1, "py")
        create_python_job(@project, job_python_2, "py")
        create_python_job(@project, job_python_3, "py")
        get_jobs(@project[:id], nil)
        expect_status(200)
        expect(json_body[:items].count).to eq 3
        expect(json_body[:count]).to eq 3
      end
      it "should delete created job" do
        create_python_job(@project, job_python_1, "py")
        delete_job(@project[:id], job_python_1)
        expect_status(204)
      end
    end
  end
end