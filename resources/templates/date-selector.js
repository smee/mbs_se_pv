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
	// set calendar value from string after manual change.
	$(dateField).change(function(){df.val($(this).val())});
	// FIXME no namespace!
	shiftTime = function(days, months, years){
		// FIXME hard coded selectors
		var sf = $('#start-date');
		var ef = $('#end-date');
		
		var startdate = sf.DatePickerGetDate(false);
		var enddate = ef.DatePickerGetDate(false);
		
		startdate.addDays(days);
		startdate.addMonths(months);
		startdate.addYears(years);
		enddate.addDays(days);
		enddate.addMonths(months);
		enddate.addYears(years);
		
		sf.DatePickerSetDate(startdate,true);
		sf.val(sf.DatePickerGetDate(true));
		ef.DatePickerSetDate(enddate,true);
		ef.val(ef.DatePickerGetDate(true));
		// FIXME hard coded selectors		
		if($('#rerender').attr('checked')){
			$('#render-chart').trigger('click');
		}
		
		return false;
	}
	
})("%s","%s",%s, %s);
