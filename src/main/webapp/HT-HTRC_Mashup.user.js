// ==UserScript==
// @name        HT-HTRC Mashup
// @author      David Bainbridge
// @namespace   org.hathitrust.researchcenter.mashup
// @description Hybrid interface between Hathitrust and HTRC
// @version     1.1
// @grant       GM_xmlhttpRequest
// @require     http://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js
// @match       https://www.hathitrust.org/*
// @match       https://babel.hathitrust.org/cgi/ls*
// @match       https://babel.hathitrust.org/cgi/mb*
// @match       https://catalog.hathitrust.org/Search/Home*
// ==/UserScript==


var https_servlet_url="https://data.analytics.hathitrust.org/features/";

function mashupInit() {

    var $fieldset = $('form fieldset').first();
    var $search_button = $fieldset.find('button');
    $search_button.css("width","47px");
    $search_button.css("right","-55px");
    $search_button.css("font-size","95%");
    $search_button.find('span').first().hide();

    $fieldset.append('<button id="htrc-bw" class="button search" style="width:47px; right: -105px; background-image:url('+https_servlet_url + 'bookworm.png);" title="Bookworm"></button>');

    var $bw_button = $('#htrc-bw');
    $bw_button.click(function(event) {
	var input=$('#q1-input').val();
	if (input === "") {
	                return;
	        }
	event.preventDefault();

	var url_front = "https://bookworm.htrc.illinois.edu/#?%7B%22counttype%22%3A%22WordsPerMillion%22%2C%22search_limits%22%3A%5B";
	var url_end   = "%5D%7D";

	var word_front = "%7B%22word%22%3A%5B%22";
	var word_end   = "%22%5D%2C%22date_year%22%3A%7B%22%24gte%22%3A1750%2C%22%24lte%22%3A1923%7D%7D";

	var input_words = input.split(' ');
	var word_len = input_words.length;

	var full_url = url_front;

	for (var i=0; i<word_len; i++) {
	                var word = input_words[i];
	                if (i>0) { full_url += "%2C"; } // append comma
	                full_url += word_front + word + word_end;
	            }

	full_url += url_end;

	// window.location.href = "https://bookworm.htrc.illinois.edu/#?%7B%22search_limits%22%3A%5B%7B%22word%22%3A%5B%22"+input+"%22%5D%2C%22date_year%22%3A%7B%22%24gte%22%3A1750%2C%22%24lte%22%3A1923%7D%7D%5D%7D";

	window.location.href = full_url;

    });

    var url = window.location.href;
    var home_url_test = url.replace(/^https:\/\/www.hathitrust.org\//,"");
    //console.log("*** home urltest = " + home_url_test);
    if (home_url_test !== "") {
	var $login_button = $('#login-button');
	if ($login_button.length) {
	                $login_button.css('font-size','95%');
	                $login_button.css('right','-45px');
	                $login_button.css('width','80px');
	            }
    }



    /*
    each(function() {

    $('form button').each(function() {
    console.log("*** this = " + this + "jquery this = " + $(this).val());
    });
    */


}

function mashupAugmentResults()
{
    //console.log("**** mashupAugmentResults() called");
    var $results_a = $('#results_A');
    var results_a_exists = $results_a.length;

    var result_class=".result";
    var result_access_link_class=".result-access-link";

    if (!results_a_exists) {
	// Not a main search result page
	// => See if part of the collections page area
	// => If it is, set things up so it will use that instead
	    $results_a = $('#form1');
	    results_a_exists = $results_a.length;
    }

     if (!results_a_exists) {
	 // Perhaps a catalog page search?
         $results_a = $('form[name="addForm"]');
         results_a_exists = $results_a.length;
         result_class = ".resultitem";
         result_access_link_class = ".AccessLink";
    }

    if (results_a_exists) {
	//console.log("*** results_a = " + $results_a.html());

	var ids = [];
	//var file_safe_ids = [];

	//$results_a.find('.result').each(function() {
	//            var $result_access_link = $(this).find('.result-access-link');

	$results_a.find(result_class).each(function() {
	                var $result_access_link = $(this).find(result_access_link_class);

	                //console.log("*** result access link = " + $result_access_link.html());

	                var $id_link = $result_access_link.find('ul>li>a').first();
	                //console.log("*** id_link = " + $id_link.html());
	                var data_clicklog = $id_link.attr("data_clicklog");
                if (!data_clicklog) {
                    // fake one from the second <a> href (if present)
                    $id_link2 = $result_access_link.find('ul>li>a:eq(1)');
                    if ($id_link2.length>0) {
                        //console.log("*** id_link2 = " + $id_link2.html());
                        var id2_href = $id_link2.attr("href");
                        var id2 = id2_href.replace(/^https:\/\/hdl.handle.net\/\d+\//,"");
                        data_clicklog = "{\"id\":\"" + id2 + "\"}";
                    }
                    else {
                        data_clicklog="";
                    }
                }
	                //console.log("*** data clicklog= " + data_clicklog);


	                var data_json_str = data_clicklog.replace(/^[a-z]+\|/,"");
	                //console.log("*** data json= " + data_json_str);

	                var id = null;
	                if (data_json_str !== "") {
			        var data_json = JSON.parse(data_json_str);
			        id = data_json.id;
			        //console.log("*** (catalog extracted) id= '" + id + "'");
			            }
	                else {
			        var $second_id_link = $result_access_link.find('ul>li>a').eq(1);
			        //console.log("*** 2nd link len = " + $second_id_link);
			        var id_href = $second_id_link.attr("href");
			        //console.log("*** id href = " + id_href);
                    if (id_href) {
                        //console.log("*** id href = " + id_href);
                        var encoded_id = id_href.replace(/^.*id=/,"");
                        id = decodeURIComponent(encoded_id);
                    }
                }
                if (id) {
                    ids.push(id);

                    //var file_safe_id = id.replace(/:/g,"+").replace(/\//g,"=");
                    //file_safe_ids.push(file_safe_id);

                    $result_access_link.attr("id","htrc-mashup-" + id);
                    //$result_access_link.attr("id","htrc-mashup-" + file_safe_id);
                }
	            });

	var ids_str = ids.join(",");
	//var file_safe_ids_str = file_safe_ids.join(",");

	//console.log("*** ids= " + JSON.stringify(ids));
	//console.log("*** ids_str = " + ids_str);

	console.log("**** Away to request: " + https_servlet_url + "get?ids=" + encodeURIComponent(ids_str));

	var check_ids_url = https_servlet_url + "get";
	//var check_data = { "ids": encodeURIComponent(ids_str) };
	var check_data = { "ids": ids_str };

	$.post(check_ids_url,check_data)
	    .done(function(data,textStatus,response) {
		console.log("Adding in HTRC cross-checks");
		var ids_exist = data;
		for (var k in ids_exist) {
		        //console.log("*** k = '" + k + "'");

		        //var id_str = '#htrc-mashup-'+k;
		        //console.log("*** id str = " + id_str);
		        // var $id_div = $(id_str);

		        //var $id_div = $('#'+'htrc-mashup-'+k);
		        var id_div = document.getElementById('htrc-mashup-'+k);
		        //console.log("*** id_div = " + id_div);
		        var $id_div = $(id_div);

		        if (ids_exist[k]) {
			    var encoded_id=encodeURIComponent(k);
			    var ef_url = https_servlet_url + "get?download-id=" + encoded_id;
			    var atag = "<a href=\""+ ef_url +"\"><span class=\"icomoon icomoon-download\"></span>Download Extracted Features</a>";

			    $id_div.find("ul").append("<li title=\""+k+"\" style=\"color: #924a0b;\">"+atag+"</li>"); // ✓
			        }
		        else {
			    // ✗, ✘
			    $id_div.find("ul").append("<li title=\""+k+"\" style=\"color: red;\">HTRC unfriendly ✘</li>");
			        }

		        //console.log("*** id div len = " + $id_div.length);

		        //$.append("<li>HTRC friendly</li>");
		    }
		    })
	    .fail(function(esponse,textStatus,errorThrown) {
		alert( "error:"  + errorThrown);
		    });


	//console.log("*** GM httpRequest made ");
    }

    // Look for collection set download button


    var $col_download_button = $('form button[data-tracking-action="MB Download Metadata');
    if ($col_download_button.length >0) {
        var $download_form=$col_download_button.parent();
        $download_form.append("<button id=\"col-to-workset-download\" class=\"btn btn-mini\" style=\"margin-top: 12px;\"><i class=\"icomoon icomoon-download\"></i> Convert to HTRC Workset</button>");
	var $workset_download_button = $('#col-to-workset-download');
	$workset_download_button.click(function(event) {
	            event.preventDefault();

        var action = $download_form.attr("action");
        //var action_url = action;

	    var action_url = https_servlet_url + "get";

        // Extract hidden elems from, e.g. 
	    //   <input type="hidden" name="c" value="464226859" />
        //   <input type="hidden" name="a" value="download" />
        //   <input type="hidden" name="format" value="text" />
        var $hidden_inputs = $download_form.find("input[type=\"hidden\"]");
        for (var i=0; i<$hidden_inputs.length; i++) {
            var hidden_input = $hidden_inputs[i];

            if (i===0) {
                action_url += "?";
            }
            else {
                action_url += "&";
            }

            var $hidden_input = $(hidden_input);
	        var hi_name = $hidden_input.attr("name");
	        var hi_val  = $hidden_input.attr("value");
	        if (hi_name === "c") {
		    hi_name = "convert-col";
		        }
            action_url += hi_name + "=" + hi_val;

        }

	    // <title>Collections: Ancestry and Genealogy | HathiTrust Digital Library</title>
	    var all_col_title = $('title').text();
        var col_title = all_col_title.replace(/^Collections: /,"").replace(/ \|.*?$/,"");
	    action_url += "&col-title=" + encodeURIComponent(col_title);

	        console.log("workset download url: " + action_url);
	        window.location.href = action_url;
	        });

    }


}

mashupInit();

mashupAugmentResults();

