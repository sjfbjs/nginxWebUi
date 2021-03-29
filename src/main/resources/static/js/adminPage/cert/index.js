$(function() {
	form.on('switch(autoRenew)', function(data) {
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/cert/setAutoRenew',
			data: {
				id: data.value,
				autoRenew: data.elem.checked ? 1 : 0
			},
			dataType: 'json',
			success: function(data) {

				if (data.success) {
					//location.reload();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	});

	layui.use('upload', function() {
		var upload = layui.upload;
		upload.render({
			elem: '#pemBtn',
			url: '/adminPage/main/upload/',
			accept: 'file',
			done: function(res) {
				// 上传完毕回调
				if (res.success) {
					$("#pem").val(res.obj);
					$("#pemPath").html(res.obj);
				}

			},
			error: function() {
				// 请求异常回调
			}
		});

		upload.render({
			elem: '#keyBtn',
			url: '/adminPage/main/upload/',
			accept: 'file',
			done: function(res) {
				// 上传完毕回调
				if (res.success) {
					$("#key").val(res.obj);
					$("#keyPath").html(res.obj);
				}
			},
			error: function() {
				// 请求异常回调
			}
		});
	});

	form.on('select(dnsType)', function(data) {
		checkDnsType(data.value);
	});


	form.on('select(type)', function(data) {
		checkType(data.value);
	});
})


function checkDnsType(value) {
	$("#ali").hide();
	$("#dp").hide();
	$("#cf").hide();
	$("#gd").hide();
		
	$("#" + value).show();
}

function checkType(value) {
	if (value == 0) {
		$("#type0").show();
		$("#type1").hide();
	}
	if (value == 1) {
		$("#type0").hide();
		$("#type1").show();
	}
}

function add() {
	$("#id").val("");
	$("#domain").val("");
	$("#type option:first").prop("selected", true);
	$("#dnsType option:first").prop("selected", true);
	$("#aliKey").val("");
	$("#aliSecret").val("");
	$("#dpId").val("");
	$("#dpKey").val("");
	$("#cfEmail").val("");
	$("#cfKey").val("");
	$("#gdKey").val("");
	$("#gdSecret").val("");
	$("#pem").val("");
	$("#key").val("");
	$("#pemPath").html("");
	$("#keyPath").html("");
	checkType(0);
	checkDnsType('ali');

	form.render();
	showWindow(certStr.add);
}


function edit(id, clone) {
	$("#id").val(id);

	$.ajax({
		type: 'GET',
		url: ctx + '/adminPage/cert/detail',
		dataType: 'json',
		data: {
			id: id
		},
		success: function(data) {
			if (data.success) {

				var cert = data.obj;
				if (!clone) {
					$("#id").val(cert.id);
				} else {
					$("#id").val("");
				}
				$("#domain").val(cert.domain);
				$("#type").val(cert.type);
				$("#dnsType").val(cert.dnsType != null ? cert.dnsType : 'ali');
				$("#aliKey").val(cert.aliKey);
				$("#aliSecret").val(cert.aliSecret);
				$("#dpId").val(cert.dpId);
				$("#dpKey").val(cert.dpKey);
				$("#cfEmail").val(cert.cfEmail);
				$("#cfKey").val(cert.cfKey);
				$("#gdKey").val(cert.gdKey);
				$("#gdSecret").val(cert.gdSecret);
				$("#pem").val(cert.pem);
				$("#key").val(cert.key);
				$("#pemPath").html(cert.pem);
				$("#keyPath").html(cert.key);

				checkType(cert.type);
				checkDnsType(cert.dnsType != null ? cert.dnsType : 'ali');

				form.render();
				showWindow(certStr.edit);

			} else {
				layer.msg(data.msg);
			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}

function showWindow(title) {
	layer.open({
		type: 1,
		title: title,
		area: ['700px', '450px'], // 宽高
		content: $('#windowDiv')
	});
}

function addOver() {
	if ($("#domain").val() == "") {
		layer.msg(certStr.error1);
		return;
	}

	if ($("#type").val() == 0) {
		if ($("#dnsType").val() == 'ali') {
			if ($("#aliKey").val() == '' || $("#aliSecret").val() == '') {
				layer.msg(commonStr.IncompleteEntry);
				return;
			}
		}
		if ($("#dnsType").val() == 'dp') {
			if ($("#dpId").val() == '' || $("#dpKey").val() == '') {
				layer.msg(commonStr.IncompleteEntry);
				return;
			}
		}
		if ($("#dnsType").val() == 'cf') {
			if ($("#cfEmail").val() == '' || $("#cfKey").val() == '') {
				layer.msg(commonStr.IncompleteEntry);
				return;
			}
		}
		if ($("#dnsType").val() == 'gd') {
			if ($("#gdKey").val() == '' || $("#gdSecret").val() == '') {
				layer.msg(commonStr.IncompleteEntry);
				return;
			}
		}
	}

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/cert/addOver',
		data: $('#addForm').serialize(),
		dataType: 'json',
		success: function(data) {

			if (data.success) {
				location.reload();
			} else {
				layer.msg(data.msg);
			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}


function del(id) {
	if (confirm(commonStr.confirmDel)) {
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/cert/del',
			data: {
				id: id
			},
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					location.reload();
				} else {
					layer.msg(data.msg)
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}
}


function issue(id) {

	if (confirm(certStr.confirm1)) {
		layer.load();
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/cert/apply',
			data: {
				id: id,
				type: "issue"
			},
			dataType: 'json',
			success: function(data) {
				layer.closeAll();
				if (data.success) {
					layer.alert(certStr.applySuccess, function(index) {
						layer.close(index);
						location.reload();
					});
					
				} else {
					layer.open({
						type: 0,
						area: ['810px', '400px'],
						content: data.msg
					});
				}
			},
			error: function() {
				layer.closeAll();
				layer.alert(commonStr.errorInfo);
			}
		});
	}
}


function renew(id) {

	if (confirm(certStr.confirm2)) {
		layer.load();
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/cert/apply',
			data: {
				id: id,
				type: "renew"
			},
			dataType: 'json',
			success: function(data) {
				layer.closeAll();
				if (data.success) {
					layer.alert(certStr.renewSuccess, function(index) {
						layer.close(index);
						location.reload();
					});
				} else {
					layer.open({
						type: 0,
						area: ['810px', '400px'],
						content: data.msg
					});
				}
			},
			error: function() {
				layer.closeAll();
				layer.alert(commonStr.errorInfo);
			}
		});
	}
}


function selectPem() {
	rootSelect.selectOne(function(rs) {
		$("#pem").val(rs);
		$("#pemPath").html(rs);
	})
}


function selectKey() {
	rootSelect.selectOne(function(rs) {
		$("#key").val(rs);
		$("#keyPath").html(rs);
	})
}

function download(id) {
	window.open(ctx + "/adminPage/cert/download?id=" + id);
}

function clone(id){
	if (confirm(serverStr.confirmClone)) {
		edit(id, true);
	}
}