<#include "_header.ftl">

<div class="container">

<div class="row">
  <form action="/jobs" method="POST" enctype="multipart/form-data">
    <div class="col-md-6">
      <div class="panel panel-default">
        <div class="panel-heading">
          <div class="panel-title">${msg.settings}</div>
        </div>
        <div class="panel-body">

          <#if errors??>
          <div class="alert alert-danger">
            <ul class="list-unstyled">
            <#list errors as error>
              <li>${msg[error]}</li>
            </#list>
            </ul>
          </div>
          </#if>

          <#if errors?? && errors?seq_contains("error.specOrConfigMissing")>
          <div class="form-group has-error">
          <#else>
          <div class="form-group">
          </#if>
            <label for="specification" class="control-label">${msg.specification}</label>
            <select name="specification" id="specification" class="form-control">
              <option value="">${msg.noSpec}</option>
              <option value="" disabled>-------------------</option>
              <#list specifications as specification>
                <#assign name = specification?substring(specification?last_index_of("/")+1)>
                <option value="${name}">${name}</option>
              </#list>
            </select>
          </div>
          <#if errors?? && errors?seq_contains("error.specOrConfigMissing")>
          <div class="form-group has-error">
          <#else>
          <div class="form-group">
          </#if>
            <label for="configuration" class="control-label">${msg.configuration}</label>
            <select name="configuration" id="configuration" class="form-control">
              <option value="">${msg.noConfig}</option>
              <option value="" disabled>-------------------</option>
              <#list configurations as configuration>
                <#assign name = configuration?substring(configuration?last_index_of("/")+1)>
                <option value="${name}">${name}</option>
              </#list>
            </select>
          </div>
          <#if errors?? && errors?seq_contains("error.noProgram")>
          <div class="form-group has-error">
          <#else>
          <div class="form-group">
          </#if>
            <label for="programFile" class="control-label">${msg.programFile}</label>
            <input type="file" name="programFile" id="programFile">
          </div>
          <#if errors?? && errors?seq_contains("error.noProgram")>
          <div class="form-group has-error">
          <#else>
          <div class="form-group">
          </#if>
            <label for="programText" class="control-label">${msg.programText}</label>
            <textarea name="programText" id="programText" rows="3" class="form-control"></textarea>
          </div>
          <button type="submit" class="btn btn-primary">${msg.submitJob}</button>
        </div>
      </div>
    </div>

    <div class="col-md-6">
      <div class="panel panel-default">
        <div class="panel-heading">
          <div class="panel-title">${msg.options}</div>
        </div>
        <div class="panel-body">
          <div class="checkbox">
            <label for="enableOutput" class="control-label">
              <input type="checkbox" name="enableOutput" id="enableOutput" value="output.disable" checked> ${msg.enableOutput}
            </label>
          </div>
          <div class="checkbox">
            <label for="exportStatistics" class="control-label">
              <input type="checkbox" name="exportStatistics" id="exportStatistics" value="statistics.export" checked> ${msg.statisticsExport}
            </label>
          </div>
          <div class="checkbox">
            <label for="logUsedOptions" class="control-label">
              <input type="checkbox" name="logUsedOptions" id="logUsedOptions" value="log.usedOptions.export"> ${msg.logUsedOptions}
            </label>
          </div>
          <div class="form-group">
            <label for="logLevel" class="control-label">${msg.logLevel}
            <select name="logLevel" id="logLevel">
              <option value="ALL">ALL</option>
              <option value="FINEST" selected>FINEST</option>
              <option value="FINER">FINER</option>
              <option value="FINE">FINE</option>
              <option value="INFO">INFO</option>
              <option value="WARNING">WARNING</option>
              <option value="SEVERE">SEVERE</option>
              <option value="OFF">OFF</option>
            </select>
            </label>
          </div>
        </div>
      </div>

      <div class="panel panel-default">
        <div class="panel-heading">
          <div class="panel-title">${msg.presetOptions}</div>
        </div>
        <div class="panel-body">
          <p>${msg.presetOptionsDescription}</p>
          <ul class="list-unstyled">
          <#list defaultOptions?keys as option>
            <li>${option} = ${defaultOptions[option]}</li>
          </#list>
          </ul>
        </div>
      </div>
    </div>
  </form>
</div>

<#include "_footer.ftl">