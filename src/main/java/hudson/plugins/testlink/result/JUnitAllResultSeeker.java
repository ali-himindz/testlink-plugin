/*
 * The MIT License
 *
 * Copyright (c) <2011> <Bruno P. Kinoshita>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.testlink.result;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.plugins.testlink.TestLinkSite;
import hudson.plugins.testlink.util.Messages;
import hudson.tasks.junit.JUnitParser;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.CaseResult;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.CustomField;

/**
 * <p>
 * Seeks for test results matching each JUnit Case Result name with the key
 * custom field.
 * </p>
 * 
 * <p>
 * Skips JUnit Case Results that were disabled.
 * </p>
 * 
 * @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
 * @author Oliver Merkel - Merkel.Oliver at web.de
 * @since 3.1
 */
public class JUnitAllResultSeeker extends AbstractJUnitResultSeeker {
	private static final Logger LOGGER = Logger
			.getLogger("hudson.plugins.testlink");

	private static final long serialVersionUID = 2278496777245515704L;

	/**
	 * @param includePattern
	 *            Include pattern used when looking for results
	 * @param keyCustomField
	 *            Key custom field to match against the results
	 * @param attachJUnitXML
	 *            Bit that enables attaching result file to TestLink
	 */
	@DataBoundConstructor
	public JUnitAllResultSeeker(String includePattern, String keyCustomField,
			boolean attachJUnitXML, boolean includeNotes) {
		super(includePattern, keyCustomField, attachJUnitXML, includeNotes);
	}

	@Extension
	public static class DescriptorImpl extends ResultSeekerDescriptor {
		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return "JUnit All Tests"; // TBD: i18n
		}
	}

	private TestCaseWrapper getAssociatedTestCase(
			TestCaseWrapper[] automatedTestCases, SuiteResult suiteResult) {
		LOGGER.log(Level.ALL,"Finding Associated Test Cases for Suite="+suiteResult.getName());
		
		for (TestCaseWrapper tc : automatedTestCases) {
			String scenario_name = tc.getKeyCustomFieldValue("SCENARIO_NAME").toLowerCase().replace(" ", "-");
			for (String classname : suiteResult.getClassNames()) {
				LOGGER.log(Level.ALL,"scenario_name="+scenario_name+" classname="+classname);
				if (classname.contains(scenario_name)) {
					LOGGER.log(Level.ALL, "Found Test Case :"+tc.getId()+":"
							+ tc.getName()+" scenario_name="+scenario_name+" for classname="+classname+" for suiteResult="+suiteResult.getName());
					return tc;
				}
			}
		}
		return null;
	}

	private ExecutionStatus getOverAllStatus(SuiteResult suiteResult) {
		ExecutionStatus status = ExecutionStatus.NOT_RUN;
		for (CaseResult caseResult : suiteResult.getCases()) {
			if (this.getExecutionStatus(caseResult) == ExecutionStatus.FAILED) {
				return ExecutionStatus.FAILED;
			}
			if (this.getExecutionStatus(caseResult) == ExecutionStatus.PASSED) {
				status = ExecutionStatus.PASSED;
			}
		}
		return status;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * hudson.plugins.testlink.result.ResultSeeker#seekAndUpdate(hudson.plugins
	 * .testlink.result.TestCaseWrapper<?>[], hudson.model.AbstractBuild,
	 * hudson.Launcher, hudson.model.BuildListener,
	 * hudson.plugins.testlink.TestLinkSite,
	 * hudson.plugins.testlink.result.Report)
	 */
	@Override
	public void seek(TestCaseWrapper[] automatedTestCases,
			AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, TestLinkSite testlink)
			throws ResultSeekerException {
		listener.getLogger().println(
				Messages.Results_JUnit_LookingForTestCases()); // i18n
		try {
			final JUnitParser parser = new JUnitParser(false);

			final TestResult testResult = parser.parse(this.includePattern,
					build, launcher, listener);
			
			for (TestCaseWrapper tc:automatedTestCases){
				LOGGER.log(Level.ALL, "Test Case ID="+tc.getId()+" name="+tc.getName());
			}
			for (SuiteResult suiteResult : testResult.getSuites()) {
				ExecutionStatus overallStatus = getOverAllStatus(suiteResult);

				TestCaseWrapper associatedTestCase = getAssociatedTestCase(
						automatedTestCases, suiteResult);
				if (associatedTestCase != null) {
					
					associatedTestCase.setExecutionStatus(overallStatus);
					LOGGER.log(Level.ALL, "New Status for Test Case :"
							+ associatedTestCase.getExecutionStatus());
					super.handleCucumberJsonResult(associatedTestCase, build,
							listener, testlink, suiteResult);
				} else {
					LOGGER.log(Level.ALL,
							"Did not find Any test case associated with the results");
				}
			}

			

		} catch (IOException e) {
			throw new ResultSeekerException(e);
		} catch (InterruptedException e) {
			throw new ResultSeekerException(e);
		}
	}

	/**
	 * @param caseResult
	 *            the case result
	 * @return NOT_RUN in case it is skipped, PASSED if it passed, and FAILED
	 *         otherwise
	 */
	private ExecutionStatus getExecutionStatus(CaseResult caseResult) {
		if (caseResult.isSkipped()) {
			return ExecutionStatus.NOT_RUN;
		} else if (caseResult.isPassed()) {
			return ExecutionStatus.PASSED;
		} else {
			return ExecutionStatus.FAILED;
		}
	}

	/**
	 * Retrieves the Notes about the JUnit test.
	 * 
	 * @param testCase
	 *            JUnit test.
	 * @return Notes about the JUnit test.
	 */
	private String getJUnitNotes(CaseResult testCase) {
		StringBuilder notes = new StringBuilder();
		notes.append(Messages.Results_JUnit_NotesForTestCase(
				testCase.getName(), testCase.getClassName(), testCase
						.getSkipCount(), testCase.getFailCount(), (testCase
						.getSuiteResult() != null ? testCase.getSuiteResult()
						.getTimestamp() : null)));

		return notes.toString();
	}

}
