(function(dateField, formattedDate, minDate, maxDate) {
	var df = $(dateField);
	df.DatePicker({
		format : 'd.m.Y',
		date : formattedDate,
		current : formattedDate,
		calendars : 1,
		onBeforeShow : function() {
			df.DatePickerSetDate(df.val(), true);
		},
		onChange : function(formatted, date) {
			df.val(formatted).DatePickerHide();
		},
		onRender : function(date) {
			return {
				disabled : date.valueOf() < minDate || date.valueOf() > maxDate,
				className : false
			}
		}
	});
	
})("%s","%s",%s, %s);