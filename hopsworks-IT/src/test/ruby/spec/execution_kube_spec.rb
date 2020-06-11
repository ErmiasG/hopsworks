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
  describe 'execution' do
    describe "#create" do
      context 'without authentication' do
        before :all do
          with_valid_project
          reset_session
        end
        it "should fail" do
          create_python_job(@project, "demo_job", 'jar')
          expect_status(401)
          expect_json(errorCode: 200003)
        end
      end
      job_types = ['py', 'ipynb']
      job_types.each do |type|
        context 'with authentication and executable ' + type do
          before :all do
            with_valid_tour_project("spark")
          end
          before :each do
            $job_name = "j_#{short_random_id}"
          end
          describe 'create, get, delete executions' do
            it "should start/stop a job and get its executions" do
              #create job
              create_python_job(@project, $job_name, type)
              job_id = json_body[:id]
              #start execution
              start_execution(@project[:id], $job_name)
              execution_id = json_body[:id]
              expect_status(201)
              expect(json_body[:state]).to eq "INITIALIZING"
              #get execution
              get_execution(@project[:id], $job_name, json_body[:id])
              expect_status(200)
              expect(json_body[:id]).to eq(execution_id)
              #wait till it's finished and start second execution
              wait_for_execution_completed(@project[:id], $job_name, json_body[:id], "FINISHED")
              #start execution
              start_execution(@project[:id], $job_name)
              execution_id = json_body[:id]
              expect_status(201)

              #get all executions of job
              get_executions(@project[:id], $job_name, "")
              expect(json_body[:items].count).to eq 2

              #check database
              num_executions = count_executions(job_id)
              expect(num_executions).to eq 2

              wait_for_execution_completed(@project[:id], $job_name, execution_id, "FINISHED")
            end
            it "should start and stop job" do
              create_python_job(@project, $job_name, type)
              expect_status(201)

              #start execution
              start_execution(@project[:id], $job_name)
              execution_id = json_body[:id]
              expect_status(201)
              stop_execution(@project[:id], $job_name, execution_id)
              expect_status(202)
              wait_for_execution_completed(@project[:id], $job_name, execution_id, "KILLED")
            end
            it "should fail to start a python job with missing files param" do
              create_python_job(@project, $job_name, type)
              config = json_body[:config]
              config[:'files'] = "hdfs:///Projects/#{@project[:projectname]}/Resources/iamnothere.txt"
              create_python_job(@project, $job_name, type, config)
              #start execution
              start_execution(@project[:id], $job_name)
              expect_status(400)
            end
            it "should start two executions in parallel" do
              create_python_job(@project, $job_name, type)
              start_execution(@project[:id], $job_name)
              expect_status(201)
              start_execution(@project[:id], $job_name)
              expect_status(201)
            end
            it "should start a job with args 123" do
              create_python_job(@project, $job_name, type)
              args = "123"
              start_execution(@project[:id], $job_name, args)
              execution_id = json_body[:id]
              expect_status(201)
              get_execution(@project[:id], $job_name, execution_id)
              expect_status(200)
              expect(json_body[:args]).to eq args
            end
            it "should run job and get out and err logs" do
              create_python_job(@project, $job_name, type)
              start_execution(@project[:id], $job_name)
              execution_id = json_body[:id]
              expect_status(201)

              wait_for_execution_completed(@project[:id], $job_name, execution_id, "FINISHED")

              #wait for log aggregation
              begin
                wait_result = wait_for_me_time(60) do
                  get_execution_log(@project[:id], $job_name, execution_id, "out")
                  { 'success' => (json_body[:log] != "No log available"), 'msg' => "wait for out log aggregation" }
                end
                expect(wait_result["success"]).to be(true), wait_result["msg"]
              rescue
                start_execution(@project[:id], $job_name)
                execution_id = json_body[:id]
                expect_status(201)
                wait_for_execution_completed(@project[:id], $job_name, execution_id, "FINISHED")
                wait_result = wait_for_me_time(60) do
                  get_execution_log(@project[:id], $job_name, execution_id, "out")
                  { 'success' => (json_body[:log] != "No log available"), 'msg' => "wait for out log aggregation" }
                end
                expect(wait_result["success"]).to be(true), wait_result["msg"]
              end
              #get out log
              get_execution_log(@project[:id], $job_name, execution_id, "out")
              expect(json_body[:type]).to eq "OUT"
              expect(json_body[:log]).to be_present

              #wait for log aggregation
              wait_result = wait_for_me_time(60) do
                get_execution_log(@project[:id], $job_name, execution_id, "err")
                { 'success' => (json_body[:log] != "No log available"), 'msg' => "wait for err log aggregation" }
              end
              expect(wait_result["success"]).to be(true), wait_result["msg"]

              #get err log
              get_execution_log(@project[:id], $job_name, execution_id, "err")
              expect(json_body[:type]).to eq "ERR"
              expect(json_body[:log]).to be_present
            end
          end
        end
      end
    end
  end
end