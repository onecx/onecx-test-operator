

const runTestForm = document.getElementById('run-test-form');

runTestForm.addEventListener('submit', function (event) {
    event.preventDefault();
    requestStart();
    const formData = new FormData(runTestForm);
    var object = {};
    formData.forEach((value, key) => object[key] = value);
    var json = JSON.stringify(object);
    call(json);
});

async function call(json) {
    try {
        const requestOptions = {
          method: 'POST',
          body: json,
          headers: {
            'Content-Type': 'application/json'
          }
        };
        const response = await fetch("/ui/test", requestOptions);
        if (!response.ok) {
            if (response.status === 400) {
                const data = await response.json();
                showErrorResult('Bad request', data.errorCode, data.detail);
            } else {
                const data = await response.text();
                showErrorResult('Error', response.status, data);
            }
        }

        const data = await response.json();
        showResult(data);
    } finally {
        requestFinished();
    }
}

function showErrorResult(msg, code, data) {
    console.error(msg, data);
    const status = document.getElementById('result-status');
    status.textContent=msg;
    status.classList.remove("label-success");
    status.classList.add("label-error");
    document.getElementById('result-error').textContent=code + ':  ' + data;
}

function requestFinished() {
    document.getElementById('submit').disabled=false;
    document.getElementById('progress').setAttribute('hidden', "");
}

function requestStart() {
    document.getElementById('submit').disabled=true;
    document.getElementById('progress').removeAttribute('hidden')

    const status = document.getElementById('result-status');
    status.textContent='';
    status.classList.add("label-success");
    status.classList.remove("label-error");
    document.getElementById('result-id').textContent='';
    const body = document.getElementById('result-table-body');
    body.innerHTML = '';
}

function showResult(result) {
    const status = document.getElementById('result-status');
    status.textContent=result.status;
    if (result.status == 'OK') {
        status.classList.add("label-success");
        status.classList.remove("label-error");
    } else {
        status.classList.remove("label-success");
        status.classList.add("label-error");
    }
    document.getElementById('result-id').textContent='Id: ' + result.id;

    const body = document.getElementById('result-table-body');
    for (var i=0; i<result.executions.length; i++) {
       item = result.executions[i]
        var row = addRow(body);
        addCol(row, item.url);
        addCol(row, item.path);
        addCol(row, item.proxy);
        addCol(row, item.code);
        addColStatus(row, item.status);
        addCol(row, item.error);
    }
}
function addRow(body) {
    var row =  document.createElement("tr");
    body.appendChild( row );
    return row;
}

function addCol(row, data) {
   var col =  document.createElement("td");
   col.innerHTML = data;
   row.appendChild( col );
}

function addColStatus(row, data) {
   var s = 'error';
   if (data == 'OK') {
        s = 'success';
   }
   var col =  document.createElement("td");
   col.innerHTML = '<span class="label label-' + s + ' text-tiny label-rounded" >' + data + '</span>'
   row.appendChild( col );
}

